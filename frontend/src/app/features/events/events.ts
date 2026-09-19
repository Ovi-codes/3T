import { Component, computed, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AdminEventItem, EventsService, ListedEvent } from './events.service';
import { HOURS, QUARTER_HOURS, toLocalDateTime } from './event-time';
import { AdminEventActions } from './admin-event-actions';
import { ForecastWidget } from './forecast';
import { Poster } from '../../shared/poster/poster';
import { AuthService } from '../auth/auth.service';
import { toFormErrors } from '../../core/form-errors';

/** The longest an event name may be — mirrors the backend @Size(max = 160). */
const MAX_NAME = 160;

/**
 * Increment 1: the anonymous upcoming-events list. Asks the events service for the upcoming runs
 * (already filtered and ordered soonest-first) and renders them, with a loading, empty and error
 * state. No auth — this is the public landing view.
 *
 * Increment 10a (#57): an admin (and only an admin — the server enforces it) also gets an inline
 * "Create a run" form here. The time is entered as Bucharest wall-clock (date + hour + quarter-hour)
 * and sent as a zone-less local date-time; the server applies Europe/Bucharest and binds the sole
 * location. On success the list is refreshed so the new run appears in place.
 *
 * Increment 10b (#58): an admin reads the schedule from the admin endpoint instead, which keeps
 * cancelled runs on it (read-only, badged) and carries each run's registration count — neither of
 * which the public payload contains. Every card then gets its edit / remove controls
 * ({@link AdminEventActions}), and any change they make re-reads the schedule.
 */
@Component({
  selector: 'app-events',
  imports: [DatePipe, RouterLink, ReactiveFormsModule, Poster, ForecastWidget, AdminEventActions],
  templateUrl: './events.html',
  styleUrl: './events.css',
})
export class Events {
  private readonly events$ = inject(EventsService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** null while the request is in flight; the (possibly empty) list once it lands. */
  protected readonly events = signal<ListedEvent[] | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  /** Whether to show the admin create form — hidden for everyone but an admin (UI convenience only). */
  protected readonly isAdmin = this.auth.isAdmin;

  /** How many runs a non-admin sees on the landing page — admins see the full upcoming schedule. */
  private static readonly PUBLIC_LIMIT = 4;

  /**
   * The runs to show in the upcoming grid: everyone sees the next {@link PUBLIC_LIMIT}, an admin sees
   * all of them. null while the list is still loading. The hero CTA targets {@link nextEvent}, which
   * is unaffected by the cap.
   */
  protected readonly visibleEvents = computed<ListedEvent[] | null>(() => {
    const all = this.events();
    if (all === null) {
      return null;
    }
    return this.isAdmin() ? all : all.slice(0, Events.PUBLIC_LIMIT);
  });

  /**
   * The run the hero points at: the soonest one that is still on. For everyone but an admin that's
   * simply the first, since the public list never carries a cancelled run; an admin's list does, and
   * a called-off run must not be what the page invites people to register for.
   */
  protected readonly nextEvent = computed<ListedEvent | null>(
    () => this.events()?.find((event) => !this.isCancelled(event)) ?? null,
  );

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
  protected readonly minutes = QUARTER_HOURS;

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
    // The session resolves after the page is created, so the role can flip from "not an admin" to
    // "admin" under us; re-reading on the flip is what gets an admin the richer schedule without a
    // reload. Signed out it runs exactly once, for the public list.
    effect(() => {
      this.isAdmin();
      this.load();
    });

    // Any edit clears a lingering "created" banner so it doesn't outstay the moment.
    this.createForm.valueChanges.subscribe(() => this.justCreated.set(null));
  }

  /**
   * (Re)load the upcoming runs — the admin schedule for an admin, the public list otherwise. Reused
   * by the initial view and after any admin change (create, edit, delete, cancel).
   */
  protected load(): void {
    const schedule = this.isAdmin() ? this.events$.adminList() : this.events$.list();
    schedule.subscribe({
      next: (response) => {
        this.events.set(response);
        this.error.set(null);
      },
      error: () => this.error.set('We could not load upcoming runs. Please try again shortly.'),
    });
  }

  /**
   * The admin view of a row, or null when the page is showing the public list. The two payloads are
   * genuinely different shapes, so this is the one place they're told apart — everything downstream
   * takes a definite {@link AdminEventItem}.
   */
  protected adminView(event: ListedEvent): AdminEventItem | null {
    return 'registrationCount' in event ? event : null;
  }

  /** Whether a row is a called-off run. Never true on the public list, which drops them. */
  protected isCancelled(event: ListedEvent): boolean {
    return 'status' in event && event.status === 'CANCELLED';
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
    const startDateTime = toLocalDateTime({ date: date!, hour: hour!, minute: minute! });

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
