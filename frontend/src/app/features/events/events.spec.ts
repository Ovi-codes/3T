import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { Events } from './events';
import { EventItem } from './events.service';
import { FORECAST_UNAVAILABLE } from './forecast.service';

describe('Events', () => {
  let fixture: ComponentFixture<Events>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Events],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Events);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  async function flush(events: EventItem[]): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/events').flush(events);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders the events in the order the API returns them', async () => {
    await flush([
      { id: 1, name: 'Morning 5k', startDateTime: '2026-09-05T06:00:00Z', locationName: 'Tineretului Park', city: 'Bucharest' },
      { id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z', locationName: 'Tineretului Park', city: 'Bucharest' },
    ]);
    // The hero's forecast widget loads the first event's forecast; answer it so the request verifies.
    httpMock.expectOne('/api/events/1/forecast').flush(FORECAST_UNAVAILABLE);

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
    expect(cards.length).toBe(2);
    // order is preserved: first card is the first item, second is the second
    expect(cards[0].textContent).toContain('Morning 5k');
    expect(cards[1].textContent).toContain('Evening 5k');
    // the first (soonest) run is marked as the next one
    expect(cards[0].textContent).toContain('Next');
    expect(cards[1].textContent).not.toContain('Next');
  });

  it('shows the empty state when there are no upcoming events', async () => {
    await flush([]);

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(fixture.nativeElement.querySelectorAll('[data-testid="event-item"]').length).toBe(0);
    expect(text).toContain('No upcoming runs yet');
  });

  it('shows an error message when the events cannot be loaded', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/events').error(new ProgressEvent('network error'));
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('could not load upcoming runs');
  });
});