import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import { AccountService, MyRunStat } from '../account/account.service';
import { formatFinishTime } from '../dashboard/finish-time';
import { ordinal, placeOf } from './place';
import { TrendChart } from './trend-chart';

export { ordinal } from './place';

/**
 * Increment 13 (#45): the signed-in runner's personal stats, shown on the dashboard — three headline
 * numbers (runs finished, personal best, best place), the time-trend chart, and a screen-reader-only
 * table of time and place per run. The table is the chart's accessible view; on screen, each run's
 * place is in the chart's tooltip.
 *
 * Loads GET /api/me/stats itself, with its own loading, empty and error states, so a failure here
 * never blanks the runs above it. The dashboard calls {@link reload} after a time is saved.
 */
@Component({
  selector: 'app-my-stats',
  imports: [DatePipe, TrendChart],
  templateUrl: './my-stats.html',
  styleUrl: './my-stats.css',
})
export class MyStats {
  private readonly account = inject(AccountService);

  /** null until the first response lands; the finished runs (oldest first) after. */
  protected readonly runs = signal<MyRunStat[] | null>(null);
  protected readonly error = signal(false);

  /** Newest first for the table — the order the dashboard's Past list reads in. */
  protected readonly newestFirst = computed(() => [...(this.runs() ?? [])].reverse());

  protected readonly bestTime = computed(() =>
    Math.min(...(this.runs() ?? []).map((run) => run.finishTimeSeconds)),
  );

  /** The best place — on a tie, the one against the bigger field. */
  protected readonly bestPlace = computed(() =>
    (this.runs() ?? []).reduce<MyRunStat | null>(
      (best, run) =>
        best === null ||
        run.position < best.position ||
        (run.position === best.position && run.finishers > best.finishers)
          ? run
          : best,
      null,
    ),
  );

  protected readonly formatTime = formatFinishTime;
  protected readonly ordinal = ordinal;
  protected readonly placeOf = placeOf;

  constructor() {
    this.reload();
  }

  /** Fetch the stats again — after a time is recorded, or when the runner asks to retry. */
  reload(): void {
    this.error.set(false);
    this.account.getMyStats().subscribe({
      next: (response) => this.runs.set(response.runs),
      error: () => {
        this.runs.set(null);
        this.error.set(true);
      },
    });
  }
}
