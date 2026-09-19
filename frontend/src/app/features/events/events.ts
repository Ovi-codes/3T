import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { EventItem, EventsService } from './events.service';
import { ForecastWidget } from './forecast';
import { Poster } from '../../shared/poster/poster';
import { AuthService } from '../auth/auth.service';
import { toFormErrors } from '../../core/form-errors';

/** The longest an event name may be — mirrors the backend @Size(max = 160). */
const MAX_NAME = 160;

/** Selectable hours (00–23) and quarter-hour minutes, pre-rendered as two-digit option values. */
const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));
const MINUTES = ['00', '15', '30', '45'];

/**
 * Increment 1: the anonymous upcoming-events list. Asks the events service for the upcoming runs
 * (already filtered and ordered soonest-first) and renders them, with a loading, empty and error
 * state. No auth — this is the public landing view.
 *
 * Increment 10a (#57): an admin (and only an admin — the server enforces it) also gets an inline
 * "Create a run" form here. The time is entered as Bucharest wall-clock (date + hour + quarter-hour)
 * and sent as a zone-less local date-time; the server applies Europe/Bucharest and binds the sole
 * location. On success the list is refreshed so the new run appears in place.
 */
@Component({
  selector: 'app-events',
  imports: [DatePipe, RouterLink, ReactiveFormsModule, Poster, ForecastWidget],
  templateUrl: './events.html',
  styleUrl: './events.css',
})
export class Events {
  private readonly events$ = inject(EventsService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** null while the request is in flight; the (possibly empty) list once it lands. */
  protected readonly events = signal<EventItem[] | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  /** Whether to show the admin create form — hidden for everyone but an admin (UI convenience only). */
  protected readonly isAdmin = this.auth.isAdmin;

  /** How many runs a non-admin sees on the landing page — admins see the full upcoming schedule. */
  private static readonly PUBLIC_LIMIT = 4;

  /**
   * The runs to show in the upcoming grid: everyone sees the next {@link PUBLIC_LIMIT}, an admin sees
   * all of them. null while the list is still loading. The hero CTA still targets the true next run
   * (events()[0]), which is unaffected by the cap.
   */
  protected readonly visibleEvents = computed<EventItem[] | null>(() => {
    const all = this.events();
    if (all === null) {
      return null;
    }
    return this.isAdmin() ? all : all.slice(0, Events.PUBLIC_LIMIT);
  });

  /**
   * Empty tiles that pad the last row so it never shows a bare grey grid cell. The grid is at most 4
   * columns (and 2 or 1 below that); padding the count up to a multiple of 4 fills the last row at
   * every width that shows more than one column (the single-column layout hides them — see the CSS).
   */
  protected readonly fillers = computed<number[]>(() => {
    const list = this.visibleEvents();
    if (!list || list.length === 0) {
      return [];
    }
    const missing = (4 - (list.length % 4)) % 4;
    return Array.from({ length: missing }, (_, index) => index);
  });

  protected readonly hours = HOURS;
  protected readonly minutes = MINUTES;

  /** True while the create POST is in flight, to disable the submit button. */
  protected readonly creating = signal(false);
  /** Server-side field errors from a rejected create, keyed by field (name / startDateTime). */
  protected readonly fieldErrors = signal<Record<string, string>>({});
  /** A whole-form error from a rejected create (network, or a rejection not tied to a field). */
  protected readonly formError = signal<string | null>(null);
  /** Name of the most recently created run, to announce success; cleared on the next edit. */
  protected readonly justCreated = signal<string | null>(null);

  protected readonly createForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(MAX_NAME)]],
    date: ['', [Validators.required]],
    hour: ['19', [Validators.required]],
    minute: ['30', [Validators.required]],
  });

  constructor() {
    this.load();

    // Any edit clears a lingering "created" banner so it doesn't outstay the moment.
    this.createForm.valueChanges.subscribe(() => this.justCreated.set(null));
  }

  /** (Re)load the upcoming runs. Reused by the initial view and after an admin creates one. */
  private load(): void {
    this.events$.list().subscribe({
      next: (response) => {
        this.events.set(response);
        this.error.set(null);
      },
      error: () => this.error.set('We could not load upcoming runs. Please try again shortly.'),
    });
  }

  /** The message to show under the name field: server error first, else the client-side rule. */
  protected nameFieldError(): string | null {
    const server = this.fieldErrors()['name'];
    if (server) {
      return server;
    }
    const control = this.createForm.controls.name;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Enter a name for the run.';
    }
    if (control.hasError('maxlength')) {
      return `Name must be at most ${MAX_NAME} characters.`;
    }
    return null;
  }

  /**
   * The message for the date/time row: a server rejection of the start (e.g. a past time) first,
   * then the client-side "pick a date" rule. Hour and minute always have a value, so the date is the
   * only client-side gap.
   */
  protected startFieldError(): string | null {
    const server = this.fieldErrors()['startDateTime'];
    if (server) {
      return server;
    }
    const date = this.createForm.controls.date;
    if (date.touched && date.hasError('required')) {
      return 'Choose a date for the run.';
    }
    return null;
  }

  protected create(): void {
    this.formError.set(null);
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.creating.set(true);
    this.fieldErrors.set({});
    this.justCreated.set(null);
    const { name, date, hour, minute } = this.createForm.getRawValue();
    // Wall-clock local date-time, no zone — the server reads it as Europe/Bucharest.
    const startDateTime = `${date}T${hour}:${minute}`;

    this.events$.create({ name: name!.trim(), startDateTime }).subscribe({
      next: (created) => {
        this.creating.set(false);
        // Reset back to the default time but drop the name/date so the form is ready for the next
        // run. emitEvent:false so this programmatic reset doesn't trip the valueChanges handler that
        // clears the just-created banner.
        this.createForm.reset({ name: '', date: '', hour: '19', minute: '30' }, { emitEvent: false });
        this.justCreated.set(created.name);
        this.load();
      },
      error: (response: HttpErrorResponse) => {
        this.creating.set(false);
        const { fieldErrors, formError } = toFormErrors(response);
        this.fieldErrors.set(fieldErrors);
        this.formError.set(formError);
      },
    });
  }
}
