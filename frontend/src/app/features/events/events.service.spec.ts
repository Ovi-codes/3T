import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AdminEventItem, EventItem, EventsService } from './events.service';

const EVENTS: EventItem[] = [
  { id: 1, name: 'Morning 5k', startDateTime: '2026-09-05T06:00:00Z', locationName: 'Tineretului Park', city: 'Bucharest' },
  { id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z', locationName: 'Herăstrău', city: 'Bucharest' },
];

const ADMIN_EVENT: AdminEventItem = {
  ...EVENTS[0],
  status: 'SCHEDULED',
  registrationCount: 7,
};

describe('EventsService', () => {
  let service: EventsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list fetches the upcoming events from /api/events', () => {
    let received: EventItem[] | undefined;
    service.list().subscribe((events) => (received = events));

    const request = httpMock.expectOne('/api/events');
    expect(request.request.method).toBe('GET');
    request.flush(EVENTS);

    expect(received).toEqual(EVENTS);
  });

  it('byId resolves the matching event from the list', () => {
    let received: EventItem | undefined;
    service.byId(2).subscribe((event) => (received = event));

    httpMock.expectOne('/api/events').flush(EVENTS);

    expect(received).toEqual(EVENTS[1]);
  });

  it('byId resolves to undefined when the id is not among the upcoming events', () => {
    let received: EventItem | undefined = EVENTS[0];
    service.byId(999).subscribe((event) => (received = event));

    httpMock.expectOne('/api/events').flush(EVENTS);

    expect(received).toBeUndefined();
  });

  it('byId does not cache — each call re-fetches', () => {
    service.byId(1).subscribe();
    httpMock.expectOne('/api/events').flush(EVENTS);

    service.byId(1).subscribe();
    httpMock.expectOne('/api/events').flush(EVENTS);
  });

  it('create posts the payload to the admin endpoint and returns the created event', () => {
    const created: EventItem = {
      id: 3,
      name: 'Autumn 5k',
      startDateTime: '2026-10-01T09:00:00+03:00',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
    };
    let received: EventItem | undefined;
    service.create({ name: 'Autumn 5k', startDateTime: '2026-10-01T09:00' }).subscribe((event) => (received = event));

    const request = httpMock.expectOne('/api/admin/events');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Autumn 5k', startDateTime: '2026-10-01T09:00' });
    request.flush(created);

    expect(received).toEqual(created);
  });

  it('adminList fetches the admin schedule, which carries status and counts', () => {
    let received: AdminEventItem[] | undefined;
    service.adminList().subscribe((events) => (received = events));

    const request = httpMock.expectOne('/api/admin/events');
    expect(request.request.method).toBe('GET');
    request.flush([ADMIN_EVENT]);

    expect(received).toEqual([ADMIN_EVENT]);
  });

  it('update puts the payload to the admin endpoint and returns the edited event', () => {
    const edited: AdminEventItem = { ...ADMIN_EVENT, name: 'Renamed 5k' };
    let received: AdminEventItem | undefined;
    service
      .update(1, { name: 'Renamed 5k', startDateTime: '2026-10-01T09:00' })
      .subscribe((event) => (received = event));

    const request = httpMock.expectOne('/api/admin/events/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Renamed 5k', startDateTime: '2026-10-01T09:00' });
    request.flush(edited);

    expect(received).toEqual(edited);
  });

  it('remove deletes the event through the admin endpoint', () => {
    let completed = false;
    service.remove(1).subscribe({ complete: () => (completed = true) });

    const request = httpMock.expectOne('/api/admin/events/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(completed).toBe(true);
  });

  it('cancel posts to the cancel endpoint and returns the cancelled event', () => {
    const cancelled: AdminEventItem = { ...ADMIN_EVENT, status: 'CANCELLED' };
    let received: AdminEventItem | undefined;
    service.cancel(1).subscribe((event) => (received = event));

    const request = httpMock.expectOne('/api/admin/events/1/cancel');
    expect(request.request.method).toBe('POST');
    request.flush(cancelled);

    expect(received).toEqual(cancelled);
  });
});
