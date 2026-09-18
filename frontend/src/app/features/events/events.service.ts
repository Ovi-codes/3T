import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/** Shape of one item from GET /api/events — mirrors the backend EventResponse record. */
export interface EventItem {
  id: number;
  name: string;
  startDateTime: string;
  locationName: string;
  city: string;
}

/**
 * The admin create-event payload (issue #57). `startDateTime` is a wall-clock local date-time
 * (`YYYY-MM-DDTHH:mm`, no zone) — the server reads it as Europe/Bucharest time. `location` is not
 * sent: the server binds the sole Bucharest location.
 */
export interface CreateEventInput {
  name: string;
  startDateTime: string;
}

/**
 * Owns the events resource and its DTO. The one home for the events API contract, so components
 * hold view state only and never build `/api` URLs themselves.
 */
@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);

  /** Upcoming events, already filtered to upcoming and ordered soonest-first by the server. */
  list(): Observable<EventItem[]> {
    return this.http.get<EventItem[]>('/api/events');
  }

  /**
   * One event by id, or `undefined` if it isn't among the upcoming runs (unknown or past). There's
   * no `GET /api/events/{id}` yet, so this fetches the upcoming list and finds it; there's
   * deliberately no cache — each call re-fetches. The seam lets the fetch-all be swapped for a real
   * by-id endpoint later with no change to callers.
   */
  byId(id: number): Observable<EventItem | undefined> {
    return this.list().pipe(map((events) => events.find((event) => event.id === id)));
  }

  /**
   * Admin-only: create an event (issue #57). Posts to `/api/admin/events`, which the server gates on
   * `ROLE_ADMIN`; a non-admin or anonymous caller is refused there (403 / 401), not here.
   */
  create(input: CreateEventInput): Observable<EventItem> {
    return this.http.post<EventItem>('/api/admin/events', input);
  }
}
