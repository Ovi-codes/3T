import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

/** Shape of one item from GET /api/events — mirrors the backend EventResponse record. */
export interface EventItem {
  id: number;
  name: string;
  startDateTime: string;
  locationName: string;
  city: string;
}

/**
 * Increment 1: the anonymous upcoming-events list. Calls GET /api/events (already
 * filtered to upcoming and ordered by date, soonest first) and renders it, with a
 * loading, empty and error state. No auth — this is the public landing view.
 */
@Component({
  selector: 'app-events',
  imports: [DatePipe, RouterLink],
  templateUrl: './events.html',
  styleUrl: './events.css',
})
export class Events {
  private readonly http = inject(HttpClient);

  /** null while the request is in flight; the (possibly empty) list once it lands. */
  protected readonly events = signal<EventItem[] | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.http.get<EventItem[]>('/api/events').subscribe({
      next: (response) => this.events.set(response),
      error: () => this.error.set('We could not load upcoming runs. Please try again shortly.'),
    });
  }
}