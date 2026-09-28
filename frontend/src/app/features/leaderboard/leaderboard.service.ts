import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** One runner's line on a leaderboard — mirrors the backend LeaderboardEntry record. */
export interface LeaderboardEntry {
  /** Standard competition ranking (1, 2, 2, 4); null for a runner who hasn't entered a time. */
  position: number | null;
  /** First name + last initial ("Ana P.") — the only identity the public page shows (charter §7). */
  runnerName: string;
  /** Whole seconds, or null when the runner hasn't entered a time. */
  finishTimeSeconds: number | null;
}

/** Body of GET /api/events/{id}/leaderboard — mirrors the backend LeaderboardResponse record. */
export interface RunLeaderboard {
  eventId: number;
  eventName: string;
  startDateTime: string;
  locationName: string;
  city: string;
  /** Already in finishing order: fastest first, ties by who registered first, no time last. */
  entries: LeaderboardEntry[];
}

/**
 * A past run's public results (#44). Anonymous — no session needed. The server refuses a run that
 * hasn't taken place or was cancelled (409) and an unknown one (404), each with an `eventId` message.
 */
@Injectable({ providedIn: 'root' })
export class LeaderboardService {
  private readonly http = inject(HttpClient);

  forRun(eventId: number): Observable<RunLeaderboard> {
    return this.http.get<RunLeaderboard>(`/api/events/${eventId}/leaderboard`);
  }
}
