import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { AccountService, MyRegistration } from '../account/account.service';
import { formatFinishTime, parseFinishTime } from './finish-time';
import { toFormErrors } from '../../core/form-errors';

/** The sensible 5k window, in seconds — mirrors the backend @Min(600) / @Max(3600). */
const FASTEST = 600;
const SLOWEST = 3600;

/**
 * Increment 11 (#43): the runner's own finish time on one past run of their dashboard — shown once
 * recorded, and entered or corrected in place.
 */
@Component({
  selector: 'app-finish-time-entry',
  imports: [ReactiveFormsModule],
  templateUrl: './finish-time-entry.html',
  styleUrl: './finish-time-entry.css',
  host: { '[class.is-editing]': 'editing()' },
})
export class FinishTimeEntry {
  private readonly account = inject(AccountService);

  /** The past run this time belongs to, as the dashboard listed it. */
  readonly run = input.required<MyRegistration>();

  /**
   * Announced with the server's updated row once a time is saved — the dashboard swaps it into its
   * list, so the server's answer stays the one source of truth.
   */
  readonly recorded = output<MyRegistration>();

  /** The recorded time as a clock time, or null when none has been entered yet. */
  protected readonly time = computed(() => {
    const seconds = this.run().finishTimeSeconds;
    return seconds === null ? null : formatFinishTime(seconds);
  });

  protected readonly editing = signal(false);
  /** True while the save is in flight, so it can't be sent twice. */
  protected readonly busy = signal(false);
  /** Why the typed time can't be saved — the client's own check, or the server's answer. */
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    time: new FormControl('', { nonNullable: true }),
  });

  /** The time input, focused when the form opens. */
  private readonly timeField = viewChild<ElementRef<HTMLInputElement>>('timeField');
  /** Whichever of "Add your time" / "Edit" is showing, refocused when the form closes. */
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  /** Set on close so the effect returns focus once the trigger is back in the DOM. */
  private readonly returnFocus = signal(false);

  constructor() {
    // Opening the form removes the button that had focus; move it onto the input instead of <body>.
    effect(() => {
      if (this.editing()) {
        this.timeField()?.nativeElement.focus();
      }
    });

    // Closing it (discard or save) brings a trigger back; hand focus to it once it has rendered.
    effect(() => {
      const trigger = this.trigger();
      if (this.returnFocus() && trigger) {
        trigger.nativeElement.focus();
        this.returnFocus.set(false);
      }
    });
  }

  protected startEdit(): void {
    this.form.reset({ time: this.time() ?? '' });
    this.error.set(null);
    this.editing.set(true);
  }

  /** Close the form, having changed nothing. */
  protected discard(): void {
    this.error.set(null);
    this.close();
  }

  private close(): void {
    this.editing.set(false);
    this.returnFocus.set(true);
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    const seconds = parseFinishTime(this.form.controls.time.value);
    if (seconds === null) {
      this.error.set('Enter your time as mm:ss — for example 24:31.');
      return;
    }
    if (seconds < FASTEST) {
      this.error.set("That's faster than 10:00 — check your time.");
      return;
    }
    if (seconds > SLOWEST) {
      this.error.set("That's slower than 1:00:00 — check your time.");
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.account.recordFinishTime(this.run().registrationId, seconds).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.close();
        this.recorded.emit(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        // One message slot: the time's own field error (range, run not yet taken place, cancelled),
        // else the registration's (not found), else the generic fallback.
        const { fieldErrors, formError } = toFormErrors(err);
        this.error.set(
          fieldErrors['finishTimeSeconds'] ?? fieldErrors['registrationId'] ?? formError,
        );
      },
    });
  }
}
