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
}
