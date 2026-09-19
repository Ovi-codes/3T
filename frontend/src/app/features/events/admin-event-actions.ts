import { Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { AdminEventItem, EventsService } from './events.service';
import { HOURS, minuteOptions, toLocalDateTime, toStartFields } from './event-time';
import { toFormErrors } from '../../core/form-errors';

/** The longest an event name may be — mirrors the backend @Size(max = 160). */
const MAX_NAME = 160;

/** The longest a cancellation reason may be — mirrors the backend @Size(max = 200). */
const MAX_REASON = 200;

/**
 * The standard cancellation reasons the picker offers, newest curation owned here on the client (the
 * backend takes the chosen wording as free text — issue #58). Each string is exactly what the
 * registrant sees in the email, so a standard pick reads the same every time.
 */
const STANDARD_REASONS = [
  'Severe weather',
  'Unsafe course conditions',
  'Too few volunteers to run it safely',
  'The venue is unavailable',
];

/** The picker's free-text choice — a sentinel that can't collide with a real reason. */
const OTHER = 'Other…';

/** What the card is showing: the plain actions, the edit form, or the remove confirmation. */
type Mode = 'idle' | 'editing' | 'removing';

/**
 * Increment 10b (#58): the admin controls on one run's card — edit, and the remove decision.
 *
 * Removal is deliberately two different actions (ADR-0001). A run nobody has signed up for was
 * likely created in error, so it can be deleted outright; as soon as anyone is registered the only
 * way out is to cancel it, which keeps the run and its registrations, drops it from the public list
 * and emails everyone registered. The server enforces both rules — this component only makes the
 * right one reachable and says what will happen before it does.
 *
 * A cancelled run is terminal: nothing here is offered for it, it just says so.
 */
@Component({
  selector: 'app-admin-event-actions',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-event-actions.html',
  styleUrl: './admin-event-actions.css',
})
export class AdminEventActions {
  private readonly events$ = inject(EventsService);
  private readonly fb = inject(FormBuilder);

  /** The run these controls act on, as the admin list returned it. */
  readonly event = input.required<AdminEventItem>();

  /**
   * Announced after the server has accepted an edit, a delete or a cancel — the page reloads the
   * schedule rather than this component patching a row, so one source of truth stays the server.
   */
  readonly changed = output<void>();

  protected readonly mode = signal<Mode>('idle');
  /** True while a request is in flight, to disable the controls that would repeat it. */
  protected readonly busy = signal(false);
  /** Server-side errors from a rejected call, keyed by field (name / startDateTime / event). */
  protected readonly fieldErrors = signal<Record<string, string>>({});
  /** A whole-form error (network, or a rejection with no readable envelope). */
  protected readonly formError = signal<string | null>(null);

  protected readonly hours = HOURS;

  /** Whether the run can still be changed at all — cancellation is terminal. */
  protected readonly editable = computed(() => this.event().status !== 'CANCELLED');

  /** A run nobody has registered for can be deleted outright; otherwise cancelling is the only way out. */
  protected readonly deletable = computed(() => this.event().registrationCount === 0);

  /** The quarter hours, widened to keep a run already starting on some other minute (see event-time). */
  protected readonly minutes = computed(() =>
    minuteOptions(toStartFields(this.event().startDateTime).minute),
  );

  protected readonly editForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(MAX_NAME)]],
    date: ['', [Validators.required]],
    hour: ['', [Validators.required]],
    minute: ['', [Validators.required]],
  });

  /** The picked reason (a standard one or the `Other…` sentinel); `customText` holds the free text. */
  protected readonly cancelForm = this.fb.group({
    reason: ['', [Validators.required]],
    customText: ['', [Validators.maxLength(MAX_REASON)]],
  });

  /** The standard reasons the picker lists, plus a free-text `Other…`, both owned on the client. */
  protected readonly reasons = STANDARD_REASONS;
  protected readonly otherReason = OTHER;

  /** Whether the picked reason is the free-text one, so the custom-text field is shown and required. */
  protected showCustom(): boolean {
    return this.cancelForm.controls.reason.value === OTHER;
  }

  /** Open the edit form on the run as it currently stands, in Bucharest wall-clock time. */
  protected startEdit(): void {
    const event = this.event();
    const { date, hour, minute } = toStartFields(event.startDateTime);
    this.editForm.reset({ name: event.name, date, hour, minute });
    this.clearErrors();
    this.mode.set('editing');
  }

  protected startRemove(): void {
    this.clearErrors();
    // Only a run with registrations is cancelled, and only cancelling needs a reason — preselect the
    // first standard one so the picker is never in a blank state.
    if (!this.deletable()) {
      this.cancelForm.reset({ reason: this.reasons[0], customText: '' });
    }
    this.mode.set('removing');
  }

  /** Back to the plain actions, having changed nothing. */
  protected dismiss(): void {
    this.clearErrors();
    this.mode.set('idle');
  }

  protected save(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    const { name, date, hour, minute } = this.editForm.getRawValue();
    this.run(
      this.events$.update(this.event().id, {
        name: name!.trim(),
        startDateTime: toLocalDateTime({ date: date!, hour: hour!, minute: minute! }),
      }),
    );
  }

  protected remove(): void {
    this.run(this.events$.remove(this.event().id));
  }

  protected cancelRun(): void {
    const choice = this.cancelForm.controls.reason.value ?? '';
    // A standard pick is sent as-is; "Other…" sends the admin's own (trimmed) wording instead.
    const reason =
      choice === OTHER ? (this.cancelForm.controls.customText.value ?? '').trim() : choice;
    if (!reason) {
      this.cancelForm.markAllAsTouched();
      return;
    }
    this.run(this.events$.cancel(this.event().id, { reason }));
  }

  /**
   * One shape for all three calls: on success close back to the plain actions and tell the page to
   * reload; on a rejection stay open and show why, so the admin can fix the edit or think again.
   */
  private run(call: Observable<unknown>): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.clearErrors();
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.mode.set('idle');
        this.changed.emit();
      },
      error: (response: HttpErrorResponse) => {
        this.busy.set(false);
        const { fieldErrors, formError } = toFormErrors(response);
        this.fieldErrors.set(fieldErrors);
        this.formError.set(formError);
      },
    });
  }

  private clearErrors(): void {
    this.fieldErrors.set({});
    this.formError.set(null);
  }

  /**
   * The message under a field: the server's rejection (keyed by `key`) first, else — once `control`
   * has been touched and is invalid — the client-side `rule`. The shared shape behind the
   * field-level getters, so each only names its server key, its control, and its own wording.
   */
  private fieldError(key: string, control: AbstractControl, rule: () => string): string | null {
    const server = this.fieldErrors()[key];
    if (server) {
      return server;
    }
    if (!control.touched || control.valid) {
      return null;
    }
    return rule();
  }

  /** The message under the name field: the server's rejection first, else the client-side rule. */
  protected nameError(): string | null {
    return this.fieldError('name', this.editForm.controls.name, () =>
      this.editForm.controls.name.hasError('required')
        ? 'Enter a name for the run.'
        : `Name must be at most ${MAX_NAME} characters.`,
    );
  }

  /** The message under the date/time row. Hour and minute always hold a value, so only the date can be blank. */
  protected startError(): string | null {
    return this.fieldError(
      'startDateTime',
      this.editForm.controls.date,
      () => 'Choose a date for the run.',
    );
  }

  /** A rejection about the run as a whole (already cancelled, has registrations, or a network failure). */
  protected generalError(): string | null {
    return this.fieldErrors()['event'] ?? this.formError();
  }

  /** The message under the reason picker: the server's rejection first, else the client-side rule. */
  protected reasonError(): string | null {
    return this.fieldError(
      'reason',
      this.cancelForm.controls.reason,
      () => 'Choose a reason for cancelling.',
    );
  }

  /**
   * The message under the custom-reason field, shown only for the free-text reason. This is a
   * client-side guard only: the free text is sent as `reason`, so a server rejection of it comes
   * back under `reason` and surfaces on the picker via {@link reasonError}.
   */
  protected customError(): string | null {
    if (!this.showCustom()) {
      return null;
    }
    const control = this.cancelForm.controls.customText;
    if (!control.touched) {
      return null;
    }
    if (!(control.value ?? '').trim()) {
      return 'Enter a reason for cancelling.';
    }
    return control.hasError('maxlength') ? `Reason must be at most ${MAX_REASON} characters.` : null;
  }
}
