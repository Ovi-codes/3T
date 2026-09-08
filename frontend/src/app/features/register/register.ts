import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { EventItem, EventsService } from '../events/events.service';
import { RegistrationResult, RegistrationsService } from './registrations.service';
import { AuthService } from '../auth/auth.service';
import { toFormErrors } from '../../core/form-errors';

/**
 * A name has to look like a name: at least one letter, so "12345" (only digits) is rejected even
 * though it clears the minimum length. Empty is left to the required validator.
 */
function nameNotOnlyNumbers(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  return value && /^\d+$/.test(value) ? { onlyDigits: true } : null;
}

/**
 * Stricter than Angular's Validators.email, which accepts "a@a". Require a domain with a dot and a
 * real extension (2+ chars), so an address without a valid extension is rejected before submit.
 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

type LoadState = 'loading' | 'ready' | 'missing';

/**
 * Increment 2 (core loop): register for one upcoming run. Loads the event named in the route,
 * takes a name + email, and on success shows a confirmation view. Validation runs client-side
 * before submit; the server's field errors are surfaced against the same inputs, so a duplicate
 * or a rejected email reads the same way whether the browser or the API caught it.
 *
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private readonly events$ = inject(EventsService);
  private readonly registrations$ = inject(RegistrationsService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  /** The run being registered for; null until the lookup resolves. */
  protected readonly event = signal<EventItem | null>(null);
  /** loading → ready (event found) or missing (unknown or no-longer-upcoming). */
  protected readonly loadState = signal<LoadState>('loading');
  /** True while the POST is in flight, to disable the submit button. */
  protected readonly submitting = signal(false);
  /** Set once the registration is recorded — the form is replaced by the confirmation. */
  protected readonly confirmation = signal<RegistrationResult | null>(null);
  /** Server-side field errors, keyed by field name (name / email). */
  protected readonly fieldErrors = signal<Record<string, string>>({});
  /** A whole-form error (network, or a rejection not tied to a field). */
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), nameNotOnlyNumbers]],
    email: ['', [Validators.required, Validators.pattern(EMAIL_PATTERN)]],
  });

  /** The confirmation heading, focused when it replaces the form so success is announced. */
  private readonly confirmationHeading = viewChild<ElementRef<HTMLElement>>('confirmationHeading');

  constructor() {
    // Prefill from the signed-in account so a logged-in user doesn't retype their details (#38).
    // Runs when `user()` resolves (it starts undefined until /me answers) and only while the form is
    // untouched, so a user's own edits are never overwritten and an anonymous visitor (user() null)
    // keeps the empty form.
    effect(() => {
      const account = this.auth.user();
      if (account && this.form.pristine) {
        this.form.patchValue({ name: account.name ?? '', email: account.email });
      }
    });

    // On success the form is swapped for the confirmation; move focus to its heading so a
    // screen-reader / keyboard user is taken to the outcome rather than left on the vanished form.
    effect(() => {
      if (this.confirmation()) {
        this.confirmationHeading()?.nativeElement.focus();
      }
    });

    const eventId = Number(this.route.snapshot.paramMap.get('eventId'));
    // byId resolves to undefined for an unknown or no-longer-upcoming id.
    this.events$.byId(eventId).subscribe({
      next: (match) => {
        this.event.set(match ?? null);
        this.loadState.set(match ? 'ready' : 'missing');
      },
      error: () => this.loadState.set('missing'),
    });
  }

  /** The message to show under a field: server error first, else the client-side rule. */
  protected controlError(field: 'name' | 'email'): string | null {
    const server = this.fieldErrors()[field];
    if (server) {
      return server;
    }
    const control = this.form.controls[field];
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return field === 'name' ? 'Enter your name.' : 'Enter your email.';
    }
    if (field === 'name') {
      if (control.hasError('minlength')) {
        return 'Name must be at least 3 characters.';
      }
      if (control.hasError('onlyDigits')) {
        return 'Name can’t be only numbers.';
      }
    }
    if (field === 'email' && control.hasError('pattern')) {
      return 'Enter a valid email address.';
    }
    return null;
  }

  protected submit(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const event = this.event();
    if (!event) {
      return;
    }

    this.submitting.set(true);
    this.fieldErrors.set({});
    const { name, email } = this.form.getRawValue();

    this.registrations$.create({ eventId: event.id, name: name!, email: email! }).subscribe({
      next: (result) => {
        this.confirmation.set(result);
        this.submitting.set(false);
      },
      error: (response: HttpErrorResponse) => {
        this.submitting.set(false);
        const { fieldErrors, formError } = toFormErrors(response);
        const { eventId, ...fields } = fieldErrors;
        this.fieldErrors.set(fields);
        // An event-level rejection (past / unknown) has no field to sit under; otherwise fall
        // back to the generic message when the envelope was missing.
        this.formError.set(eventId ?? formError);
      },
    });
  }
}
