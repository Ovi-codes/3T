import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { LeaderboardService, RunLeaderboard } from './leaderboard.service';

const LEADERBOARD: RunLeaderboard = {
  eventId: 7,
  eventName: 'Tineretului parkrun',
  startDateTime: '2026-09-22T15:30:00Z',
  locationName: 'Tineretului Park',
  city: 'Bucharest',
  entries: [
    { position: 1, runnerName: 'Ana P.', finishTimeSeconds: 1300 },
    { position: null, runnerName: 'Elena S.', finishTimeSeconds: null },
  ],
};

describe('LeaderboardService', () => {
  let service: LeaderboardService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LeaderboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches one run’s public leaderboard', () => {
    let received: RunLeaderboard | undefined;
    service.forRun(7).subscribe((leaderboard) => (received = leaderboard));

    const request = httpMock.expectOne('/api/events/7/leaderboard');
    expect(request.request.method).toBe('GET');
    request.flush(LEADERBOARD);

    expect(received).toEqual(LEADERBOARD);
  });
});
