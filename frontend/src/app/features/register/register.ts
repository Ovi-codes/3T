import { Component, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { EventItem } from '../events/events';

/** The successful POST /api/registrations response — mirrors the backend RegistrationResponse. */
interface RegistrationResult {
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
  email: string;
}

type LoadState = 'loading' | 'ready' | 'missing';

/**
 * Increment 2 (core loop): register for one upcoming run. Loads the event named in the route,
 * takes a name + email, and on success shows a confirmation view. Validation runs client-side
 * before submit; the server's field errors are surfaced against the same inputs, so a duplicate
 * or a rejected email reads the same way whether the browser or the API caught it. No auth.
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private readonly http = inject(HttpClient);
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
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    const eventId = Number(this.route.snapshot.paramMap.get('eventId'));
    // /api/events only returns upcoming runs, so an unknown or past id simply isn't in the list.
    this.http.get<EventItem[]>('/api/events').subscribe({
      next: (events) => {
        const match = events.find((event) => event.id === eventId);
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
    if (control.hasError('email')) {
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

    this.http
      .post<RegistrationResult>('/api/registrations', { eventId: event.id, name, email })
      .subscribe({
        next: (result) => {
          this.confirmation.set(result);
          this.submitting.set(false);
        },
        error: (response: HttpErrorResponse) => {
          this.submitting.set(false);
          const errors = response.error?.errors as Record<string, string> | undefined;
          if (errors && typeof errors === 'object') {
            const { eventId, ...fields } = errors;
            this.fieldErrors.set(fields);
            // An event-level rejection (past / unknown) has no field to sit under.
            this.formError.set(eventId ?? null);
          } else {
            this.formError.set('Something went wrong — please try again.');
          }
        },
      });
  }
}
