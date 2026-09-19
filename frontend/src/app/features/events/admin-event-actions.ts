import { Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { AdminEventItem, EventsService } from './events.service';
import { HOURS, minuteOptions, toLocalDateTime, toStartFields } from './event-time';
import { toFormErrors } from '../../core/form-errors';

/** The longest an event name may be — mirrors the backend @Size(max = 160). */
const MAX_NAME = 160;

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
    this.run(this.events$.cancel(this.event().id));
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

  /** The message under the name field: the server's rejection first, else the client-side rule. */
  protected nameError(): string | null {
    const server = this.fieldErrors()['name'];
    if (server) {
      return server;
    }
    const control = this.editForm.controls.name;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required')
      ? 'Enter a name for the run.'
      : `Name must be at most ${MAX_NAME} characters.`;
  }

  /** The message under the date/time row. Hour and minute always hold a value, so only the date can be blank. */
  protected startError(): string | null {
    const server = this.fieldErrors()['startDateTime'];
    if (server) {
      return server;
    }
    const date = this.editForm.controls.date;
    return date.touched && date.hasError('required') ? 'Choose a date for the run.' : null;
  }

  /** A rejection about the run as a whole (already cancelled, has registrations, or a network failure). */
  protected generalError(): string | null {
    return this.fieldErrors()['event'] ?? this.formError();
  }
}
