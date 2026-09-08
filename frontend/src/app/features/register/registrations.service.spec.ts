import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { RegistrationResult, RegistrationsService } from './registrations.service';

describe('RegistrationsService', () => {
  let service: RegistrationsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RegistrationsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('create POSTs the registration body to /api/registrations and returns the result', () => {
    const result: RegistrationResult = {
      eventName: 'Tineretului parkrun',
      startDateTime: '2026-09-05T06:00:00Z',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
      email: 'ana.pop@example.com',
    };
    let received: RegistrationResult | undefined;
    service
      .create({ eventId: 3, name: 'Ana Pop', email: 'ana.pop@example.com' })
      .subscribe((res) => (received = res));

    const request = httpMock.expectOne('/api/registrations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ eventId: 3, name: 'Ana Pop', email: 'ana.pop@example.com' });
    request.flush(result);

    expect(received).toEqual(result);
  });
});
