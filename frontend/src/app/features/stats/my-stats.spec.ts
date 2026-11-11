import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { MyRunStat, MyStats as MyStatsBody } from '../account/account.service';
import { MyStats, ordinal } from './my-stats';

function stat(overrides: Partial<MyRunStat> = {}): MyRunStat {
  return {
    eventId: 1,
    eventName: 'Tineretului parkrun',
    startDateTime: '2026-09-01T15:30:00Z',
    locationName: 'Tineretului Park',
    city: 'Bucharest',
    finishTimeSeconds: 1500,
    position: 3,
    finishers: 20,
    ...overrides,
  };
}

/** Oldest first, as the API sends them. Best time 23:10 (2nd run); best place 2nd (3rd run). */
const STATS: MyStatsBody = {
  runs: [
    stat({
      eventId: 1,
      eventName: 'First run',
      startDateTime: '2026-09-01T15:30:00Z',
      finishTimeSeconds: 1500,
      position: 9,
      finishers: 20,
    }),
    stat({
      eventId: 2,
      eventName: 'Second run',
      startDateTime: '2026-09-08T15:30:00Z',
      finishTimeSeconds: 1390,
      position: 4,
      finishers: 25,
    }),
    stat({
      eventId: 3,
      eventName: 'Third run',
      startDateTime: '2026-09-15T15:30:00Z',
      finishTimeSeconds: 1417,
      position: 2,
      finishers: 31,
    }),
  ],
};

describe('MyStats', () => {
  let fixture: ComponentFixture<MyStats>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyStats],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MyStats);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function query(testid: string): HTMLElement | null {
    return element().querySelector(`[data-testid="${testid}"]`);
  }

  async function flush(body: MyStatsBody): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/stats').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('says it is loading while the stats are on their way', () => {
    fixture.detectChanges();

    expect(query('stats-loading')!.textContent).toContain('Loading your stats');
    httpMock.expectOne('/api/me/stats');
  });

  it('invites a runner with no finished runs to add a time', async () => {
    await flush({ runs: [] });

    expect(query('stats-empty')!.textContent).toContain('No stats yet');
    expect(element().querySelector('svg')).toBeNull();
    expect(query('stats-table')).toBeNull();
  });

  it('shows the headline numbers: runs finished, personal best and best place', async () => {
    await flush(STATS);

    expect(query('stat-runs')!.textContent).toContain('3');
    expect(query('stat-best-time')!.textContent).toContain('23:10');
    expect(query('stat-best-place')!.textContent).toContain('2nd');
  });

  it('draws the time trend', async () => {
    await flush(STATS);

    expect(element().querySelectorAll('[data-testid="trend-point"]').length).toBe(3);
  });

  it('keeps each run’s time and place in a table for screen readers only, newest first', async () => {
    await flush(STATS);

    // The chart's accessible view: hidden on screen (it repeats the chart), read out by assistive tech.
    const table = query('stats-table') as HTMLTableElement;
    expect(table.classList).toContain('sr-only');
    expect(table.querySelector('caption')).not.toBeNull();
    const rows = Array.from(table.querySelectorAll('tbody tr')).map((row) =>
      Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent!.trim()),
    );
    expect(rows.map((row) => row[1])).toEqual(['Third run', 'Second run', 'First run']);
    expect(rows[0]).toContain('23:37');
    expect(rows[0]).toContain('2nd of 31');
  });

  it('says what went wrong when the stats cannot be loaded, and tries again on request', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/me/stats').error(new ProgressEvent('network error'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('stats-error')!.textContent).toContain('could not load your stats');

    (query('stats-retry') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(query('stats-loading')).not.toBeNull();
    httpMock.expectOne('/api/me/stats').flush(STATS);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('stats-error')).toBeNull();
    expect(query('stat-runs')!.textContent).toContain('3');
  });

  it('fetches fresh stats when asked to reload — a new time changes them', async () => {
    await flush({ runs: [] });

    fixture.componentInstance.reload();
    httpMock.expectOne('/api/me/stats').flush(STATS);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('stats-empty')).toBeNull();
    expect(query('stat-runs')!.textContent).toContain('3');
  });
});

describe('ordinal', () => {
  it.each([
    [1, '1st'],
    [2, '2nd'],
    [3, '3rd'],
    [4, '4th'],
    [11, '11th'],
    [12, '12th'],
    [13, '13th'],
    [21, '21st'],
    [22, '22nd'],
    [23, '23rd'],
    [101, '101st'],
    [111, '111th'],
  ])('writes %i as %s', (place, expected) => {
    expect(ordinal(place)).toBe(expected);
  });
});
