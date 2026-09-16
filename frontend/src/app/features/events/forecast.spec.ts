import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ForecastWidget } from './forecast';
import { Forecast, FORECAST_UNAVAILABLE } from './forecast.service';

const AVAILABLE: Forecast = {
  available: true,
  date: '2026-09-18',
  condition: 'rain',
  description: 'Light rain',
  temperatureMaxC: 22.4,
  temperatureMinC: 11.9,
  precipitationProbabilityMax: 55,
};

describe('ForecastWidget', () => {
  let fixture: ComponentFixture<ForecastWidget>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForecastWidget],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ForecastWidget);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  async function load(id: number, respond: (req: ReturnType<HttpTestingController['expectOne']>) => void): Promise<void> {
    fixture.componentRef.setInput('eventId', id);
    fixture.detectChanges();
    respond(httpMock.expectOne(`/api/events/${id}/forecast`));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders the forecast when one is available', async () => {
    await load(1, (req) => req.flush(AVAILABLE));

    const widget = fixture.nativeElement.querySelector('[data-testid="forecast"]');
    expect(widget).not.toBeNull();
    const text = (widget as HTMLElement).textContent ?? '';
    expect(text).toContain('22°');
    expect(text).toContain('12°'); // 11.9 rounded
    expect(text).toContain('Light rain');
    expect(text).toContain('55% rain');
  });

  it('renders nothing when no forecast is available', async () => {
    await load(1, (req) => req.flush(FORECAST_UNAVAILABLE));

    expect(fixture.nativeElement.querySelector('[data-testid="forecast"]')).toBeNull();
  });

  it('degrades to nothing when the request fails (the page is never blocked)', async () => {
    await load(1, (req) => req.error(new ProgressEvent('network error')));

    expect(fixture.nativeElement.querySelector('[data-testid="forecast"]')).toBeNull();
  });
});
