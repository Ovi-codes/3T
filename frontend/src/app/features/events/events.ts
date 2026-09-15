import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { EventItem, EventsService } from './events.service';
import { Poster } from '../../shared/poster/poster';

/**
 * Increment 1: the anonymous upcoming-events list. Asks the events service for the upcoming runs
 * (already filtered and ordered soonest-first) and renders them, with a loading, empty and error
 * state. No auth — this is the public landing view.
 */
@Component({
  selector: 'app-events',
  imports: [DatePipe, RouterLink, Poster],
  templateUrl: './events.html',
  styleUrl: './events.css',
})
export class Events {
  private readonly events$ = inject(EventsService);

  /** null while the request is in flight; the (possibly empty) list once it lands. */
  protected readonly events = signal<EventItem[] | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.events$.list().subscribe({
      next: (response) => this.events.set(response),
      error: () => this.error.set('We could not load upcoming runs. Please try again shortly.'),
    });
  }
}
