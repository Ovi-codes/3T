import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { AuthService } from '../auth/auth.service';

/** One of the user's registrations — mirrors the backend MyRegistration record. */
export interface MyRegistration {
  registrationId: number;
  eventId: number;
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
  /**
   * Whether the run has been called off (#58). A cancelled run stays on the dashboard, badged, until
   * its date passes — the registration is kept, so the runner can see what happened to it.
   */
  cancelled: boolean;
  /**
   * The runner's own finish time for a past run, in whole seconds — null until they record one
   * (#43). Seconds on the wire; the dashboard formats it as mm:ss.
   */
  finishTimeSeconds: number | null;
}

/** Body of GET /api/me/registrations: the two buckets the dashboard shows. */
export interface MyRegistrations {
  upcoming: MyRegistration[];
  past: MyRegistration[];
}

/** One finished run in the user's personal stats (#45) — mirrors the backend MyRunStat record. */
export interface MyRunStat {
  eventId: number;
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
  /** The runner's own time, in whole seconds. */
  finishTimeSeconds: number;
  /** Their place on the run's leaderboard — a tied time shares its place (1, 2, 2, 4). */
  position: number;
  /** How many runners recorded a time on that run, so the place reads "2nd of 31". */
  finishers: number;
}

/**
 * Body of GET /api/me/stats (#45): the user's finished runs — past, not cancelled, with a time —
 * oldest first. Empty until they record a time.
 */
export interface MyStats {
  runs: MyRunStat[];
}

/**
 * The signed-in user's own account and data — everything under /api/me/*: their registrations
 * (the dashboard, Increment 4) and the GDPR data rights (charter §7) to export everything held
 * about them and to erase the account. Kept separate from AuthService, which owns the session and
 * credentials, because account management grows its own surface in later versions. Every call
 * relies on the session cookie the browser sends automatically (same-origin via the dev proxy).
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  /**
   * The user's registrations, already split into upcoming/past and ordered by the server
   * (upcoming soonest-first, past most-recent-first).
   */
  getMyRegistrations(): Observable<MyRegistrations> {
    return this.http.get<MyRegistrations>('/api/me/registrations');
  }

  /** The user's personal stats: time and place on each finished run, oldest first (#45). */
  getMyStats(): Observable<MyStats> {
    return this.http.get<MyStats>('/api/me/stats');
  }

  /**
   * Record or correct the runner's own finish time on one of their past registrations
   * (PUT /api/me/registrations/{id}/finish-time, #43). Resolves to the updated dashboard row.
   */
  recordFinishTime(registrationId: number, finishTimeSeconds: number): Observable<MyRegistration> {
    return this.http.put<MyRegistration>(`/api/me/registrations/${registrationId}/finish-time`, {
      finishTimeSeconds,
    });
  }

  /**
   * GDPR right to portability: everything held about the account as a downloadable file
   * (GET /api/me/export). Returned as a blob so the caller can save it as-is.
   */
  exportData(): Observable<Blob> {
    return this.http.get('/api/me/export', { responseType: 'blob' });
  }

  /**
   * GDPR right to erasure: delete the account and its data (DELETE /api/me). The server ends the
   * session too, so mirror it locally — the now-deleted principal is signed out.
   */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>('/api/me').pipe(tap(() => this.auth.markSignedOut()));
  }
}
