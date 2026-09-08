import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { EventItem, EventsService } from './events.service';

const EVENTS: EventItem[] = [
  { id: 1, name: 'Morning 5k', startDateTime: '2026-09-05T06:00:00Z', locationName: 'Tineretului Park', city: 'Bucharest' },
  { id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z', locationName: 'Herăstrău', city: 'Bucharest' },
];

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
});
