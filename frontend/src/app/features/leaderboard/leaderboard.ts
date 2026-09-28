import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { formatFinishTime } from '../dashboard/finish-time';
import { LeaderboardService, RunLeaderboard } from './leaderboard.service';

/** The podium: positions 1–3 earn a medal (tied runners share theirs). */
const MEDALS: Record<number, { kind: string; label: string }> = {
  1: { kind: 'gold', label: 'Gold medal' },
  2: { kind: 'silver', label: 'Silver medal' },
  3: { kind: 'bronze', label: 'Bronze medal' },
};

/**
 * Increment 12 (#44): a past run's public results. Anyone can open it — no session needed. Renders
 * the server's ranking as-is (the order and the shared positions of tied runners are the server's
 * call); runners who haven't entered a time sit at the bottom as "Time not entered".
 *
 * A run with no results — not yet run, cancelled, or unknown — shows the server's reason in place of
 * the table, so the page explains itself rather than showing an empty list.
 */
@Component({
  selector: 'app-leaderboard',
  imports: [DatePipe, RouterLink],
  templateUrl: './leaderboard.html',
  styleUrl: './leaderboard.css',
})
export class Leaderboard {
  private readonly leaderboards = inject(LeaderboardService);
  private readonly route = inject(ActivatedRoute);

  protected readonly user = inject(AuthService).user;

  /** null while the request is in flight; the run and its ranked runners once it lands. */
  protected readonly leaderboard = signal<RunLeaderboard | null>(null);
  /** The server's reason this run has no results (409 not run / cancelled, 404 unknown). */
  protected readonly unavailable = signal<string | null>(null);
  /** Set only if the call fails for any other reason, so the page fails visibly, not blankly. */
  protected readonly error = signal<string | null>(null);

  /** "2 of 3 runners have entered a time." — so the no-time rows at the bottom read as expected. */
  protected readonly summary = computed(() => {
    const entries = this.leaderboard()?.entries ?? [];
    const timed = entries.filter((entry) => entry.finishTimeSeconds !== null).length;
    const runners = entries.length === 1 ? 'runner' : 'runners';
    return `${timed} of ${entries.length} ${runners} ${timed === 1 ? 'has' : 'have'} entered a time.`;
  });

  protected readonly formatTime = formatFinishTime;

  protected medal(position: number | null): { kind: string; label: string } | null {
    return position === null ? null : (MEDALS[position] ?? null);
  }

  constructor() {
    const eventId = Number(this.route.snapshot.paramMap.get('eventId'));
    this.leaderboards.forRun(eventId).subscribe({
      next: (response) => this.leaderboard.set(response),
      error: (response: HttpErrorResponse) => {
        const reason = response.error?.errors?.eventId as string | undefined;
        if (reason && (response.status === 404 || response.status === 409)) {
          this.unavailable.set(reason);
        } else {
          this.error.set('We couldn’t load the results. Please try again shortly.');
        }
      },
    });
  }
}
