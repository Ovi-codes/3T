import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { Events } from './events';
import { AdminEventItem, EventItem, ListedEvent } from './events.service';
import { FORECAST_UNAVAILABLE } from './forecast.service';
import { AuthService } from '../auth/auth.service';

/** A stand-in for AuthService that only exposes the one thing the events page reads: isAdmin. */
class FakeAuthService {
  readonly isAdmin = signal(false);
}

const EVENT_1: EventItem = {
  id: 1,
  name: 'Morning 5k',
  startDateTime: '2026-09-05T06:00:00Z',
  locationName: 'Tineretului Park',
  city: 'Bucharest',
};

/** The same run as the admin list returns it — scheduled, nobody registered, unless overridden. */
function adminEvent(overrides: Partial<AdminEventItem> = {}): AdminEventItem {
  return { ...EVENT_1, status: 'SCHEDULED', registrationCount: 0, ...overrides };
}

describe('Events', () => {
  let fixture: ComponentFixture<Events>;
  let httpMock: HttpTestingController;
  let auth: FakeAuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Events],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useClass: FakeAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Events);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService) as unknown as FakeAuthService;
  });

  afterEach(() => httpMock.verify());

  /**
   * Render, answer the schedule request (the admin one for an admin), and — when there's a run still
   * on — the hero's forecast request for it.
   */
  async function flush(events: ListedEvent[]): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(auth.isAdmin() ? '/api/admin/events' : '/api/events').flush(events);
    await fixture.whenStable();
    fixture.detectChanges();
    const next = events.find((event) => !('status' in event) || event.status !== 'CANCELLED');
    if (next) {
      httpMock.expectOne(`/api/events/${next.id}/forecast`).flush(FORECAST_UNAVAILABLE);
    }
  }

  function click(testid: string): void {
    (query(testid) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function query(testid: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testid}"]`);
  }

  function setField(testid: string, value: string): void {
    const el = query(testid) as HTMLInputElement | HTMLSelectElement;
    el.value = value;
    el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  }

  it('renders the events in the order the API returns them', async () => {
    await flush([
      EVENT_1,
      { id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z', locationName: 'Tineretului Park', city: 'Bucharest' },
    ]);

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Morning 5k');
    expect(cards[1].textContent).toContain('Evening 5k');
    expect(cards[0].textContent).toContain('Next');
    expect(cards[1].textContent).not.toContain('Next');
  });

  it('caps a non-admin at the next four upcoming runs', async () => {
    const many: EventItem[] = Array.from({ length: 6 }, (_, i) => ({
      id: i + 1,
      name: `Run ${i + 1}`,
      startDateTime: `2026-09-${String(5 + i).padStart(2, '0')}T06:00:00Z`,
      locationName: 'Tineretului Park',
      city: 'Bucharest',
    }));
    await flush(many);

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
    expect(cards.length).toBe(4);
    expect(cards[0].textContent).toContain('Run 1');
    expect(cards[3].textContent).toContain('Run 4');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Run 5');
  });

  it('shows an admin the full upcoming list, beyond the first four', async () => {
    auth.isAdmin.set(true);
    const many: AdminEventItem[] = Array.from({ length: 6 }, (_, i) =>
      adminEvent({
        id: i + 1,
        name: `Run ${i + 1}`,
        startDateTime: `2026-09-${String(5 + i).padStart(2, '0')}T06:00:00Z`,
      }),
    );
    await flush(many);

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
    expect(cards.length).toBe(6);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Run 6');
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

  it('hides the admin create form from a non-admin', async () => {
    await flush([EVENT_1]);
    expect(query('admin-create')).toBeNull();
  });

  it('shows the admin create form to an admin', async () => {
    auth.isAdmin.set(true);
    await flush([adminEvent()]);

    expect(query('admin-create')).not.toBeNull();
    expect(query('create-name')).not.toBeNull();
    expect(query('create-date')).not.toBeNull();
    expect(query('create-submit')).not.toBeNull();
  });

  it('creates a run — posts the local date-time, then refreshes the list', async () => {
    auth.isAdmin.set(true);
    await flush([adminEvent()]);

    setField('create-name', 'Autumn Night 5k');
    setField('create-date', '2026-10-01');
    setField('create-hour', '18');
    setField('create-minute', '30');

    query('create-submit')!.closest('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const post = httpMock.expectOne('/api/admin/events');
    expect(post.request.method).toBe('POST');
    // Bucharest wall-clock, zone-less — the server applies the zone.
    expect(post.request.body).toEqual({ name: 'Autumn Night 5k', startDateTime: '2026-10-01T18:30' });
    const created: EventItem = {
      id: 2,
      name: 'Autumn Night 5k',
      startDateTime: '2026-10-01T18:30:00+03:00',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
    };
    post.flush(created);
    await fixture.whenStable();
    fixture.detectChanges();

    // The list is refetched so the new run shows in place (first event id is unchanged, so the
    // hero forecast is not re-requested).
    httpMock
      .expectOne('/api/admin/events')
      .flush([adminEvent(), adminEvent({ ...created, id: 2 })]);
    await fixture.whenStable();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
    expect(cards.length).toBe(2);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Autumn Night 5k');
    expect(query('create-success')?.textContent).toContain('Autumn Night 5k');
  });

  it('surfaces a server field error against the start and does not refresh', async () => {
    auth.isAdmin.set(true);
    await flush([adminEvent()]);

    setField('create-name', "Yesterday's run");
    setField('create-date', '2020-01-01');

    query('create-submit')!.closest('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectOne('/api/admin/events').flush(
      { errors: { startDateTime: 'The start must be in the future.' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    // The error sits under the date/time row; no refetch happened (afterEach verify would catch one).
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('The start must be in the future.');
  });

  describe('the admin schedule (#58)', () => {
    it('shows each run’s registration count and its controls', async () => {
      auth.isAdmin.set(true);
      await flush([adminEvent({ registrationCount: 7 })]);

      const card = fixture.nativeElement.querySelector('[data-testid="event-item"]') as HTMLElement;
      expect(card.textContent).toContain('7 registered');
      expect(query('event-edit')).not.toBeNull();
      expect(query('event-remove')).not.toBeNull();
    });

    it('keeps counts and controls off the public page', async () => {
      await flush([EVENT_1]);

      expect(query('event-count')).toBeNull();
      expect(query('event-edit')).toBeNull();
      expect(query('event-remove')).toBeNull();
    });

    it('badges a cancelled run and takes away its Register link', async () => {
      auth.isAdmin.set(true);
      await flush([
        adminEvent({ id: 1, name: 'Called off 5k', status: 'CANCELLED', registrationCount: 3 }),
        adminEvent({ id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z' }),
      ]);

      const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
      expect(cards[0].querySelector('[data-testid="event-cancelled"]')).not.toBeNull();
      expect(cards[0].querySelector('[data-testid="event-register"]')).toBeNull();
      expect(cards[1].querySelector('[data-testid="event-register"]')).not.toBeNull();
    });

    it('points the hero and the Next tag at the first run that is still on', async () => {
      auth.isAdmin.set(true);
      // The forecast request flush() answers is for run 2 — the first one still on.
      await flush([
        adminEvent({ id: 1, name: 'Called off 5k', status: 'CANCELLED', registrationCount: 3 }),
        adminEvent({ id: 2, name: 'Evening 5k', startDateTime: '2026-09-12T17:00:00Z' }),
      ]);

      expect(query('hero-cta')?.getAttribute('href')).toBe('/register/2');
      const cards = fixture.nativeElement.querySelectorAll('[data-testid="event-item"]');
      expect(cards[0].textContent).not.toContain('Next');
      expect(cards[1].textContent).toContain('Next');
    });

    it('reloads the schedule once a run is deleted', async () => {
      auth.isAdmin.set(true);
      await flush([adminEvent({ id: 1, registrationCount: 0 })]);

      click('event-remove');
      click('remove-delete');
      httpMock.expectOne('/api/admin/events/1').flush(null);
      await fixture.whenStable();
      fixture.detectChanges();

      httpMock.expectOne('/api/admin/events').flush([]);
      await fixture.whenStable();
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain('No upcoming runs yet');
    });
  });
});
