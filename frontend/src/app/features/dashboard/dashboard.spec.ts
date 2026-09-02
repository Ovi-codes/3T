import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { Dashboard, MyRegistrations } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  async function flush(runs: MyRegistrations): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/registrations').flush(runs);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function run(overrides: Partial<MyRegistrations['upcoming'][number]> = {}) {
    return {
      registrationId: 1,
      eventId: 1,
      eventName: 'Tineretului parkrun',
      startDateTime: '2026-09-05T06:00:00Z',
      locationName: 'Tineretului Park',
      city: 'Bucharest',
      ...overrides,
    };
  }

  function section(testid: 'section-upcoming' | 'section-past'): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testid}"]`)!;
  }

  it('renders the two buckets from the API, each in its own section (CS-4/CS-5)', async () => {
    await flush({
      upcoming: [run({ registrationId: 10, eventId: 10, eventName: 'Autumn 5k' })],
      past: [run({ registrationId: 20, eventId: 20, eventName: 'Summer 5k' })],
    });

    const upcoming = section('section-upcoming');
    const past = section('section-past');
    expect(upcoming.querySelectorAll('[data-testid="upcoming-item"]').length).toBe(1);
    expect(upcoming.textContent).toContain('Autumn 5k');
    // The past run belongs under Past, not Upcoming.
    expect(upcoming.textContent).not.toContain('Summer 5k');
    expect(past.querySelectorAll('[data-testid="past-item"]').length).toBe(1);
    expect(past.textContent).toContain('Summer 5k');
  });

  it('marks the soonest upcoming run as the next one, and only that one', async () => {
    await flush({
      upcoming: [
        run({ registrationId: 1, eventName: 'Soonest' }),
        run({ registrationId: 2, eventName: 'Later' }),
      ],
      past: [],
    });

    const items = section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]');
    expect(items[0].textContent).toContain('Next');
    expect(items[1].textContent).not.toContain('Next');
  });

  it('shows the upcoming empty state while past still lists runs', async () => {
    await flush({ upcoming: [], past: [run()] });

    expect(section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]').length).toBe(0);
    expect(section('section-upcoming').textContent).toContain('No upcoming runs yet');
    expect(section('section-past').querySelectorAll('[data-testid="past-item"]').length).toBe(1);
  });

  it('shows the past empty state while upcoming still lists runs', async () => {
    await flush({ upcoming: [run()], past: [] });

    expect(section('section-past').querySelectorAll('[data-testid="past-item"]').length).toBe(0);
    expect(section('section-past').textContent).toContain('No past runs yet');
    expect(section('section-upcoming').querySelectorAll('[data-testid="upcoming-item"]').length).toBe(1);
  });

  it('shows an error message when the runs cannot be loaded', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/registrations').error(new ProgressEvent('network error'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not load your runs');
  });
});
