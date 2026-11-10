import { PlotArea, plotTrend } from './trend-geometry';

/** A 600×200 chart with a 50px gutter left (the time ticks) and 20px elsewhere. */
const AREA: PlotArea = { width: 600, height: 200, top: 20, right: 20, bottom: 20, left: 50 };
const PLOT_TOP = AREA.top;
const PLOT_BOTTOM = AREA.height - AREA.bottom;

describe('plotTrend', () => {
  it('spaces the runs evenly from the left edge of the plot to the right, in order', () => {
    const { points } = plotTrend([1500, 1450, 1420], AREA);

    expect(points.map((point) => point.x)).toEqual([50, 315, 580]);
  });

  it('puts a lone run in the middle of the plot', () => {
    const { points } = plotTrend([1500], AREA);

    expect(points).toHaveLength(1);
    expect(points[0].x).toBe(315);
    expect(points[0].y).toBeGreaterThanOrEqual(PLOT_TOP);
    expect(points[0].y).toBeLessThanOrEqual(PLOT_BOTTOM);
  });

  it('plots a faster time lower down — a better run shows as a dip', () => {
    const { points } = plotTrend([1500, 1420, 1610], AREA);

    expect(points[1].y).toBeGreaterThan(points[0].y);
    expect(points[2].y).toBeLessThan(points[0].y);
  });

  it('keeps every run inside the plot', () => {
    const { points } = plotTrend([1122, 1500, 2950], AREA);

    for (const point of points) {
      expect(point.y).toBeGreaterThanOrEqual(PLOT_TOP);
      expect(point.y).toBeLessThanOrEqual(PLOT_BOTTOM);
    }
  });

  it('marks the time axis in whole minutes that cover every run, fastest at the bottom', () => {
    const times = [1417, 1500, 1610];
    const { ticks } = plotTrend(times, AREA);

    expect(ticks.length).toBeGreaterThanOrEqual(2);
    expect(ticks.length).toBeLessThanOrEqual(6);
    for (const tick of ticks) {
      expect(tick.seconds % 60).toBe(0);
    }
    // Ascending time runs up the axis.
    for (let i = 1; i < ticks.length; i++) {
      expect(ticks[i].seconds).toBeGreaterThan(ticks[i - 1].seconds);
      expect(ticks[i].y).toBeLessThan(ticks[i - 1].y);
    }
    expect(ticks[0].seconds).toBeLessThanOrEqual(Math.min(...times));
    expect(ticks.at(-1)!.seconds).toBeGreaterThanOrEqual(Math.max(...times));
    expect(ticks[0].y).toBe(PLOT_BOTTOM);
    expect(ticks.at(-1)!.y).toBe(PLOT_TOP);
  });

  it('steps the axis in wider, still round, intervals when the times are far apart', () => {
    const { ticks } = plotTrend([1200, 2400], AREA);

    expect(ticks.length).toBeLessThanOrEqual(6);
    const step = ticks[1].seconds - ticks[0].seconds;
    expect([120, 300, 600]).toContain(step);
  });

  it('still draws a usable axis when every time is the same', () => {
    const { points, ticks } = plotTrend([1500, 1500], AREA);

    expect(ticks.length).toBeGreaterThanOrEqual(2);
    expect(points[0].y).toBe(points[1].y);
    expect(points[0].y).toBeGreaterThan(PLOT_TOP);
    expect(points[0].y).toBeLessThan(PLOT_BOTTOM);
  });

  it('traces the line through every run, first to last', () => {
    const { points, line } = plotTrend([1500, 1450, 1420], AREA);

    const drawn = line
      .trim()
      .split(/\s*[ML]\s*/)
      .filter(Boolean)
      .map((pair) => pair.split(',').map(Number));
    expect(line.trim().startsWith('M')).toBe(true);
    expect(drawn).toEqual(points.map((point) => [point.x, point.y]));
  });

  it('has nothing to draw without runs', () => {
    expect(plotTrend([], AREA)).toEqual({ points: [], line: '', ticks: [] });
  });
});
