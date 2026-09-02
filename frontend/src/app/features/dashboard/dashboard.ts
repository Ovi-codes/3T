import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/** One of the user's registrations — mirrors the backend MyRegistration record. */
export interface MyRegistration {
  registrationId: number;
  eventId: number;
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
}

/** Body of GET /api/me/registrations: the two buckets the dashboard shows. */
export interface MyRegistrations {
  upcoming: MyRegistration[];
  past: MyRegistration[];
}

/**
 * Increment 4: the runner's own runs, split into the ones ahead (CS-4) and the ones done (CS-5).
 * Calls GET /api/me/registrations — already split and ordered by the server (upcoming soonest-first,
 * past most-recent-first) — and renders each bucket with its own empty state.
 *
 * It only renders behind the auth guard, so `user()` is set and the request carries the session
 * cookie by the time this shows. An anonymous visitor never reaches here — the guard sends them to
 * /login (CS-6, UI side).
 */
@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;

  /** null while the request is in flight; the two buckets once it lands. */
  protected readonly runs = signal<MyRegistrations | null>(null);
  /** Set only if the call fails, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.http.get<MyRegistrations>('/api/me/registrations').subscribe({
      next: (response) => this.runs.set(response),
      error: () => this.error.set('We could not load your runs. Please try again shortly.'),
    });
  }

  protected logout(): void {
    // Either way, land on the login page — the local session is cleared regardless.
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
