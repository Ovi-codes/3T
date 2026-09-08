import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** The POST /api/registrations request body. */
export interface RegistrationRequest {
  eventId: number;
  name: string;
  email: string;
}

/** The successful POST /api/registrations response — mirrors the backend RegistrationResponse. */
export interface RegistrationResult {
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
  email: string;
}

/**
 * Owns the registrations write and its DTOs. The registration POST stays anonymous, but the server
 * attributes it to the session account when there is one (charter — Auth §Registration linkage).
 */
@Injectable({ providedIn: 'root' })
export class RegistrationsService {
  private readonly http = inject(HttpClient);

  /** CS-1 (core loop): register for one upcoming run. */
  create(req: RegistrationRequest): Observable<RegistrationResult> {
    return this.http.post<RegistrationResult>('/api/registrations', req);
  }
}
