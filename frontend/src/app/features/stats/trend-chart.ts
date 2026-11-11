import {
  Component,
  DestroyRef,
  ElementRef,
  LOCALE_ID,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe, formatDate } from '@angular/common';

import { MyRunStat } from '../account/account.service';
import { formatFinishTime } from '../dashboard/finish-time';
import { placeOf } from './place';
import { PlotArea, plotTrend } from './trend-geometry';

/** The chart's height, and the width it assumes until it has measured its container. */
const HEIGHT = 260;
const FALLBACK_WIDTH = 640;
/**
 * Gutters around the plot: room for the best-time label under the lowest dip, then the dates, below;
 * time ticks left.
 */
const GUTTER = { top: 16, right: 24, bottom: 60, left: 52 };

/**
 * Increment 13 (#45): the runner's finish times as a line, oldest run on the left. Hand-drawn SVG —
 * one series doesn't justify a charting dependency — sized to its container in real pixels, so the
 * labels stay legible on a phone rather than shrinking with a scaled viewBox.
 *
 * Faster times sit lower, so a better run shows as a dip. The personal best is marked and labelled; every
 * other value is on hover, and in full in the stats table next to it — that table is the accessible
 * view of this data, so the chart itself is one `role="img"` with a summary for its name.
 */
@Component({
  selector: 'app-trend-chart',
  imports: [DatePipe],
  templateUrl: './trend-chart.html',
  styleUrl: './trend-chart.css',
})
export class TrendChart {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly locale = inject(LOCALE_ID);
  private readonly destroyRef = inject(DestroyRef);

  /** The finished runs, oldest first — as the API sends them. */
  readonly runs = input.required<MyRunStat[]>();

  protected readonly height = HEIGHT;
  protected readonly width = signal(FALLBACK_WIDTH);

  private readonly area = computed<PlotArea>(() => ({
    width: this.width(),
    height: HEIGHT,
    ...GUTTER,
  }));

  protected readonly plot = computed(() =>
    plotTrend(
      this.runs().map((run) => run.finishTimeSeconds),
      this.area(),
    ),
  );

  /** The personal best — the first run on the fastest time. */
  protected readonly bestIndex = computed(() => {
    const times = this.runs().map((run) => run.finishTimeSeconds);
    return times.indexOf(Math.min(...times));
  });

  protected readonly plotTop = GUTTER.top;
  protected readonly plotLeft = GUTTER.left;
  protected readonly plotRight = computed(() => this.width() - GUTTER.right);
  protected readonly plotBottom = HEIGHT - GUTTER.bottom;

  /** The run under the pointer, or null when the pointer isn't over the chart. */
  protected readonly active = signal<number | null>(null);
  protected readonly activePoint = computed(() => {
    const index = this.active();
    return index === null ? null : this.plot().points[index];
  });

  protected readonly label = computed(() => {
    const runs = this.runs();
    const best = formatFinishTime(runs[this.bestIndex()].finishTimeSeconds);
    const at = (run: MyRunStat) =>
      `${formatFinishTime(run.finishTimeSeconds)} on ${this.date(run)}`;
    if (runs.length === 1) {
      return `Finish time for 1 run: ${at(runs[0])}. Personal best ${best}.`;
    }
    return (
      `Finish times across ${runs.length} runs, oldest to newest: from ${at(runs[0])} ` +
      `to ${at(runs[runs.length - 1])}. Personal best ${best}. Lower on the chart is faster.`
    );
  });

  protected readonly formatTime = formatFinishTime;
  protected readonly placeOf = placeOf;

  constructor() {
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) => {
        const measured = Math.round(entry.contentRect.width);
        if (measured > 0) {
          this.width.set(measured);
        }
      });
      observer.observe(this.host.nativeElement);
      this.destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  /** Snap to the run nearest the pointer along the x axis — readers aim at a run, not at a dot. */
  protected onPointerMove(event: PointerEvent | MouseEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const box = svg.getBoundingClientRect();
    if (box.width === 0) {
      return;
    }
    const x = ((event.clientX - box.left) / box.width) * this.width();
    const points = this.plot().points;
    let nearest = 0;
    for (let i = 1; i < points.length; i++) {
      if (Math.abs(points[i].x - x) < Math.abs(points[nearest].x - x)) {
        nearest = i;
      }
    }
    this.active.set(nearest);
  }

  /**
   * The pointer left the chart. A touch "leaves" as soon as the finger lifts, so a tapped run's
   * tooltip stays until the next tap; a mouse leaving clears it.
   */
  protected onPointerLeave(event: PointerEvent | MouseEvent): void {
    if ('pointerType' in event && event.pointerType === 'touch') {
      return;
    }
    this.active.set(null);
  }

  /** The tooltip's anchor, as a share of the chart's box — so it follows any rendered size. */
  protected tooltipLeft(index: number): number {
    return (this.plot().points[index].x / this.width()) * 100;
  }

  protected tooltipTop(index: number): number {
    return (this.plot().points[index].y / HEIGHT) * 100;
  }

  /** On the right half, the tooltip opens to the left of the run so it never runs off the edge. */
  protected tooltipFlipped(index: number): boolean {
    return this.plot().points[index].x > this.width() / 2;
  }

  private date(run: MyRunStat): string {
    return formatDate(run.startDateTime, 'd MMM y', this.locale);
  }
}
