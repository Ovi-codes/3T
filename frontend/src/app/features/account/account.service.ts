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
}

/** Body of GET /api/me/registrations: the two buckets the dashboard shows. */
export interface MyRegistrations {
  upcoming: MyRegistration[];
  past: MyRegistration[];
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
