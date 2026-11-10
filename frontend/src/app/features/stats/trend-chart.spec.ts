import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MyRunStat } from '../account/account.service';
import { TrendChart } from './trend-chart';

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

/** Oldest first, as the API sends them: 25:00, then a PB of 23:10, then 23:37. */
const RUNS: MyRunStat[] = [
  stat({ eventId: 1, eventName: 'First run', startDateTime: '2026-09-01T15:30:00Z', finishTimeSeconds: 1500 }),
  stat({ eventId: 2, eventName: 'Second run', startDateTime: '2026-09-08T15:30:00Z', finishTimeSeconds: 1390 }),
  stat({
    eventId: 3,
    eventName: 'Third run',
    startDateTime: '2026-09-15T15:30:00Z',
    finishTimeSeconds: 1417,
    position: 2,
    finishers: 31,
  }),
];

describe('TrendChart', () => {
  let fixture: ComponentFixture<TrendChart>;

  async function render(runs: MyRunStat[]): Promise<HTMLElement> {
    fixture = TestBed.createComponent(TrendChart);
    fixture.componentRef.setInput('runs', runs);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function svgOf(element: HTMLElement): SVGSVGElement {
    return element.querySelector('svg')!;
  }

  /** jsdom lays nothing out, so give the chart a box: 600px wide from the page's left edge. */
  function layOut(svg: SVGSVGElement): void {
    svg.getBoundingClientRect = () =>
      ({ left: 0, top: 0, right: 600, bottom: 240, width: 600, height: 240, x: 0, y: 0 }) as DOMRect;
  }

  function pointAt(svg: SVGSVGElement, clientX: number): void {
    svg.dispatchEvent(new MouseEvent('pointermove', { clientX, clientY: 100, bubbles: true }));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TrendChart] }).compileComponents();
  });

  it('plots one point per run', async () => {
    const element = await render(RUNS);

    expect(element.querySelectorAll('[data-testid="trend-point"]').length).toBe(3);
    expect(element.querySelector('[data-testid="trend-line"]')).not.toBeNull();
  });

  it('describes the trend to screen readers in its accessible name', async () => {
    const svg = svgOf(await render(RUNS));

    expect(svg.getAttribute('role')).toBe('img');
    const label = svg.getAttribute('aria-label')!;
    expect(label).toContain('3 runs');
    expect(label).toContain('25:00'); // the first run
    expect(label).toContain('23:37'); // the latest run
    expect(label).toContain('23:10'); // the personal best
    expect(label).toContain('Lower on the chart is faster');
  });

  it('labels the time axis in clock time', async () => {
    const element = await render(RUNS);

    const ticks = Array.from(element.querySelectorAll('[data-testid="trend-tick"]')).map((tick) =>
      tick.textContent!.trim(),
    );
    expect(ticks.length).toBeGreaterThanOrEqual(2);
    for (const tick of ticks) {
      expect(tick).toMatch(/^\d{1,2}:00$/);
    }
  });

  it('marks the personal best, and only that run', async () => {
    const element = await render(RUNS);

    const best = element.querySelectorAll('[data-testid="trend-point"].is-best');
    expect(best.length).toBe(1);
    expect(element.querySelector('[data-testid="trend-best-label"]')!.textContent).toContain('23:10');
  });

  it('labels the personal best below its point — the best run is the lowest dip', async () => {
    const element = await render(RUNS);

    const point = element.querySelector('[data-testid="trend-point"].is-best')!;
    const label = element.querySelector('[data-testid="trend-best-label"]')!;
    expect(Number(label.getAttribute('y'))).toBeGreaterThan(Number(point.getAttribute('cy')));
  });

  it('plots a single run as a lone point', async () => {
    const element = await render([RUNS[0]]);

    expect(element.querySelectorAll('[data-testid="trend-point"]').length).toBe(1);
    expect(svgOf(element).getAttribute('aria-label')).toContain('1 run');
  });

  it('shows the nearest run’s time, place and date on hover, and hides it on leave', async () => {
    const element = await render(RUNS);
    const svg = svgOf(element);
    layOut(svg);

    expect(element.querySelector('[data-testid="trend-tooltip"]')).toBeNull();

    // The pointer at the right edge snaps to the latest run.
    pointAt(svg, 590);
    const tooltip = element.querySelector('[data-testid="trend-tooltip"]')!;
    expect(tooltip.textContent).toContain('23:37');
    expect(tooltip.textContent).toContain('Third run');
    expect(tooltip.textContent).toContain('2nd of 31');
    expect(element.querySelector('[data-testid="trend-crosshair"]')).not.toBeNull();

    // At the left edge, the first run.
    pointAt(svg, 5);
    expect(element.querySelector('[data-testid="trend-tooltip"]')!.textContent).toContain('First run');

    svg.dispatchEvent(new MouseEvent('pointerleave'));
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="trend-tooltip"]')).toBeNull();
    expect(element.querySelector('[data-testid="trend-crosshair"]')).toBeNull();
  });

  it('shows the tooltip on a tap too — touch has no hover', async () => {
    const element = await render(RUNS);
    const svg = svgOf(element);
    layOut(svg);

    svg.dispatchEvent(new MouseEvent('pointerdown', { clientX: 590, clientY: 100, bubbles: true }));
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="trend-tooltip"]')!.textContent).toContain('2nd of 31');
  });
});
