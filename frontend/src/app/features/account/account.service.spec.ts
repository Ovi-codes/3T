import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AccountService } from './account.service';
import { AuthService } from '../auth/auth.service';

describe('AccountService', () => {
  let service: AccountService;
  let auth: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getMyRegistrations fetches the two buckets', () => {
    const body = { upcoming: [], past: [] };
    let received: unknown;
    service.getMyRegistrations().subscribe((runs) => (received = runs));

    const request = httpMock.expectOne('/api/me/registrations');
    expect(request.request.method).toBe('GET');
    request.flush(body);

    expect(received).toEqual(body);
  });

  it('recordFinishTime puts the time in seconds and returns the updated row', () => {
    const row = {
      registrationId: 5,
      eventId: 3,
      eventName: 'Tineretului parkrun',
      startDateTime: '2026-09-01T15:00:00Z',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
      cancelled: false,
      finishTimeSeconds: 1471,
    };
    let received: unknown;
    service.recordFinishTime(5, 1471).subscribe((updated) => (received = updated));

    const request = httpMock.expectOne('/api/me/registrations/5/finish-time');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ finishTimeSeconds: 1471 });
    request.flush(row);

    expect(received).toEqual(row);
  });

  it('exportData fetches the account data as a blob', () => {
    let received: Blob | undefined;
    service.exportData().subscribe((blob) => (received = blob));

    const request = httpMock.expectOne('/api/me/export');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['{}'], { type: 'application/json' }));

    expect(received).toBeInstanceOf(Blob);
  });

  it('deleteAccount deletes and clears the local session', () => {
    // Establish a signed-in session so we can see the delete clear it.
    auth.login('ana@example.com', 'correct horse').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ id: 1, email: 'ana@example.com' });

    service.deleteAccount().subscribe();
    const request = httpMock.expectOne('/api/me');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(auth.user()).toBeNull();
    expect(auth.isSignedIn()).toBe(false);
  });
});
