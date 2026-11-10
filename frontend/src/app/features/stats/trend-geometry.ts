/**
 * The time-trend chart's geometry (#45), kept pure so it can be tested without a DOM: where each run
 * sits, the line through them, and the time-axis ticks.
 *
 * Runs are spaced evenly left to right in the order given (oldest first) — the chart is about the
 * sequence of runs, not the gaps between dates. Time runs UP the axis, so a faster run sits lower:
 * a better run shows as a dip.
 */

/** The SVG's size and the gutters around the plot, in SVG user units. */
export interface PlotArea {
  width: number;
  height: number;
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface TrendPoint {
  x: number;
  y: number;
}

/** A gridline on the time axis: where it sits, and the whole-minute time it marks. */
export interface TimeTick {
  y: number;
  seconds: number;
}

export interface TrendPlot {
  points: TrendPoint[];
  /** An SVG path `d` through every point, first to last. */
  line: string;
  ticks: TimeTick[];
}

/** Round, whole-minute steps for the time axis, finest first. */
const STEPS = [60, 120, 300, 600, 900, 1800];
const MAX_INTERVALS = 5;

/** One decimal place is plenty for SVG and keeps the markup short. */
function round(value: number): number {
  return Math.round(value * 10) / 10;
}

export function plotTrend(times: readonly number[], area: PlotArea): TrendPlot {
  if (times.length === 0) {
    return { points: [], line: '', ticks: [] };
  }

  const fastest = Math.min(...times);
  const slowest = Math.max(...times);
  const step =
    STEPS.find((candidate) => Math.ceil(slowest / candidate) - Math.floor(fastest / candidate) <= MAX_INTERVALS) ??
    STEPS.at(-1)!;
  let low = Math.floor(fastest / step) * step;
  let high = Math.ceil(slowest / step) * step;
  if (low === high) {
    // Every time is the same (or there's one run): give the axis a step either side so the run sits
    // mid-plot rather than on an edge.
    low -= step;
    high += step;
  }

  const plotBottom = area.height - area.bottom;
  const plotHeight = area.height - area.top - area.bottom;
  const plotLeft = area.left;
  const plotWidth = area.width - area.left - area.right;
  const yFor = (seconds: number): number => round(plotBottom - ((seconds - low) / (high - low)) * plotHeight);

  const points = times.map((seconds, index) => ({
    x: round(times.length === 1 ? plotLeft + plotWidth / 2 : plotLeft + (index / (times.length - 1)) * plotWidth),
    y: yFor(seconds),
  }));

  const ticks: TimeTick[] = [];
  for (let seconds = low; seconds <= high; seconds += step) {
    ticks.push({ y: yFor(seconds), seconds });
  }

  const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join(' ');
  return { points, line, ticks };
}
