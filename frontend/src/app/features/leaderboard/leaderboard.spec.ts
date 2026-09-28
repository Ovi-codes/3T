import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Leaderboard } from './leaderboard';
import { LeaderboardEntry, RunLeaderboard } from './leaderboard.service';

function leaderboard(entries: LeaderboardEntry[]): RunLeaderboard {
  return {
    eventId: 7,
    eventName: 'Tineretului parkrun',
    startDateTime: '2026-09-22T15:30:00Z',
    locationName: 'Tineretului Park',
    city: 'Bucharest',
    entries,
  };
}

describe('Leaderboard', () => {
  let fixture: ComponentFixture<Leaderboard>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Leaderboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // The route names the run whose results to show.
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ eventId: '7' }) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Leaderboard);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function flush(body: RunLeaderboard): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/events/7/leaderboard').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function fail(status: number, body: object | null): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne('/api/events/7/leaderboard').flush(body, { status, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Each row's cells as text: [position, runner, time]. */
  function rows(): string[][] {
    return Array.from(page().querySelectorAll('[data-testid="leaderboard-row"]')).map((row) =>
      Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent!.trim()),
    );
  }

  it('shows a loading state until the results arrive', () => {
    fixture.detectChanges();

    expect(page().textContent).toContain('Loading results…');
    httpMock.expectOne('/api/events/7/leaderboard').flush(leaderboard([]));
  });

  it('names the run the results belong to', async () => {
    await flush(leaderboard([{ position: 1, runnerName: 'Ana P.', finishTimeSeconds: 1300 }]));

    expect(page().querySelector('h1')!.textContent).toContain('Tineretului parkrun');
    expect(page().textContent).toContain('Tineretului Park, Bucharest');
  });

  it('lists runners in the server’s order, with their position and a clock time', async () => {
    await flush(
      leaderboard([
        { position: 1, runnerName: 'Andrei P.', finishTimeSeconds: 1122 },
        { position: 2, runnerName: 'Ioana M.', finishTimeSeconds: 1195 },
        { position: 3, runnerName: 'Mihai C.', finishTimeSeconds: 3725 },
      ]),
    );

    expect(rows()).toEqual([
      ['1', 'Andrei P.', '18:42'],
      ['2', 'Ioana M.', '19:55'],
      ['3', 'Mihai C.', '1:02:05'],
    ]);
  });

  it('shows a shared position on each tied runner', async () => {
    await flush(
      leaderboard([
        { position: 1, runnerName: 'Andrei P.', finishTimeSeconds: 1122 },
        { position: 2, runnerName: 'Mihai C.', finishTimeSeconds: 1270 },
        { position: 2, runnerName: 'Elena D.', finishTimeSeconds: 1270 },
        { position: 4, runnerName: 'Demo R.', finishTimeSeconds: 1417 },
      ]),
    );

    expect(rows().map(([position]) => position)).toEqual(['1', '2', '2', '4']);
  });

  it('lists a runner without a time last, with no position, as "Time not entered"', async () => {
    await flush(
      leaderboard([
        { position: 1, runnerName: 'Andrei P.', finishTimeSeconds: 1122 },
        { position: null, runnerName: 'Vlad I.', finishTimeSeconds: null },
      ]),
    );

    const [, last] = rows();
    expect(last[1]).toBe('Vlad I.');
    expect(last[2]).toBe('Time not entered');
    // No number — the visible dash is decorative; screen readers hear "No position".
    expect(last[0]).not.toMatch(/\d/);
    expect(page().querySelectorAll('[data-testid="leaderboard-row"]')[1].textContent).toContain('No position');
  });

  it('says how many of the runners recorded a time', async () => {
    await flush(
      leaderboard([
        { position: 1, runnerName: 'Andrei P.', finishTimeSeconds: 1122 },
        { position: 2, runnerName: 'Ioana M.', finishTimeSeconds: 1195 },
        { position: null, runnerName: 'Vlad I.', finishTimeSeconds: null },
      ]),
    );

    expect(page().textContent).toContain('2 of 3 runners have entered a time.');
  });

  it('labels the table and its columns for assistive tech', async () => {
    await flush(leaderboard([{ position: 1, runnerName: 'Ana P.', finishTimeSeconds: 1300 }]));

    const table = page().querySelector('table')!;
    expect(table.querySelector('caption')!.textContent).toContain('Tineretului parkrun');
    const headers = Array.from(table.querySelectorAll('thead th')).map((th) => th.textContent!.trim());
    expect(headers).toEqual(['Position', 'Runner', 'Time']);
    table.querySelectorAll('thead th').forEach((th) => expect(th.getAttribute('scope')).toBe('col'));
  });

  it('invites runners in when nobody was registered for the run', async () => {
    await flush(leaderboard([]));

    expect(page().querySelector('table')).toBeNull();
    expect(page().textContent).toContain('No results for this run');
  });

  it('explains a run that has no results yet, in the server’s words', async () => {
    await fail(409, { errors: { eventId: 'This run hasn’t taken place yet, so it has no results.' } });

    expect(page().querySelector('table')).toBeNull();
    expect(page().textContent).toContain('This run hasn’t taken place yet, so it has no results.');
    expect(page().querySelector('a[href="/"]')).not.toBeNull();
  });

  it('explains a run that does not exist', async () => {
    await fail(404, { errors: { eventId: 'That run could not be found.' } });

    expect(page().textContent).toContain('That run could not be found.');
  });

  it('fails visibly when the results cannot be loaded', async () => {
    await fail(500, null);

    expect(page().querySelector('[role="alert"]')!.textContent).toContain(
      'We couldn’t load the results. Please try again shortly.',
    );
  });
});
