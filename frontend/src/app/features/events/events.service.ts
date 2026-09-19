import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/** Where a run stands — mirrors the backend EventStatus. Cancellation is terminal. */
export type EventStatus = 'SCHEDULED' | 'CANCELLED';

/** Shape of one item from GET /api/events — mirrors the backend EventResponse record. */
export interface EventItem {
  id: number;
  name: string;
  startDateTime: string;
  locationName: string;
  city: string;
}

/**
 * Shape of one item from GET /api/admin/events — the public event plus the two things only an admin
 * may see: whether the run has been cancelled, and how many people are registered for it. Neither
 * ever appears in the public payload, which is why this is a separate type rather than optional
 * fields on {@link EventItem}.
 */
export interface AdminEventItem extends EventItem {
  status: EventStatus;
  registrationCount: number;
}

/**
 * What the events page renders. The page loads the public list or — for an admin — the admin one, so
 * a row is one or the other; `'registrationCount' in event` is what tells them apart at the point of
 * use (see `Events.adminView`).
 */
export type ListedEvent = EventItem | AdminEventItem;

/**
 * The admin create/edit payload (issues #57, #58) — the whole of what an admin sets about a run.
 * `startDateTime` is a wall-clock local date-time (`YYYY-MM-DDTHH:mm`, no zone) — the server reads it
 * as Europe/Bucharest time. `location` is not sent: the server binds the sole Bucharest location.
 */
export interface EventDetailsInput {
  name: string;
  startDateTime: string;
}

/**
 * The cancel payload (issue #58): a required reason as plain text. The picker's standard reasons and
 * its free-text "other" both resolve to this one string on the client, which the server relays
 * verbatim into the cancellation email — the backend doesn't enumerate reasons.
 */
export interface CancelEventInput {
  reason: string;
}

/**
 * Owns the events resource and its DTOs. The one home for the events API contract, so components
 * hold view state only and never build `/api` URLs themselves.
 *
 * Everything under `/api/admin` is gated on `ROLE_ADMIN` by the server; a non-admin or anonymous
 * caller is refused there (403 / 401), never here — the role check in the UI only hides controls.
 */
@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);

  /** Upcoming events, already filtered to upcoming-and-not-cancelled and ordered by the server. */
  list(): Observable<EventItem[]> {
    return this.http.get<EventItem[]>('/api/events');
  }

  /**
   * Admin-only: the same upcoming window, but keeping cancelled runs (so a called-off run stays on
   * the schedule, read-only) and carrying each run's live registration count.
   */
  adminList(): Observable<AdminEventItem[]> {
    return this.http.get<AdminEventItem[]>('/api/admin/events');
  }

  /**
   * One event by id, or `undefined` if it isn't among the upcoming runs (unknown, past, or
   * cancelled). There's no `GET /api/events/{id}` yet, so this fetches the upcoming list and finds
   * it; there's deliberately no cache — each call re-fetches. The seam lets the fetch-all be swapped
   * for a real by-id endpoint later with no change to callers.
   */
  byId(id: number): Observable<EventItem | undefined> {
    return this.list().pipe(map((events) => events.find((event) => event.id === id)));
  }

  /** Admin-only: create an event (issue #57). */
  create(input: EventDetailsInput): Observable<EventItem> {
    return this.http.post<EventItem>('/api/admin/events', input);
  }

  /**
   * Admin-only: rename and/or reschedule a run (issue #58). Refused with a 409 by the server if the
   * run has been cancelled or has already taken place — both are read-only.
   */
  update(id: number, input: EventDetailsInput): Observable<AdminEventItem> {
    return this.http.put<AdminEventItem>(`/api/admin/events/${id}`, input);
  }

  /**
   * Admin-only: hard-remove a run (issue #58). The server refuses with a 409 as soon as anyone is
   * registered — {@link cancel} is the path for a run people have signed up for (ADR-0001).
   */
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/events/${id}`);
  }

  /**
   * Admin-only: call a run off (issue #58). Terminal — it drops off the public list, keeps every
   * registration, and the server emails each registrant the reason. A non-blank reason is required
   * (the server rejects a blank one with a 400).
   */
  cancel(id: number, input: CancelEventInput): Observable<AdminEventItem> {
    return this.http.post<AdminEventItem>(`/api/admin/events/${id}/cancel`, input);
  }
}
