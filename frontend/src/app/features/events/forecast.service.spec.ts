import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { Forecast, ForecastService } from './forecast.service';

const FORECAST: Forecast = {
  available: true,
  date: '2026-09-18',
  condition: 'rain',
  description: 'Light rain',
  temperatureMaxC: 22.4,
  temperatureMinC: 11.9,
  precipitationProbabilityMax: 55,
};

describe('ForecastService', () => {
  let service: ForecastService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ForecastService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('forEvent fetches the forecast for the given event id', () => {
    let received: Forecast | undefined;
    service.forEvent(7).subscribe((forecast) => (received = forecast));

    const request = httpMock.expectOne('/api/events/7/forecast');
    expect(request.request.method).toBe('GET');
    request.flush(FORECAST);

    expect(received).toEqual(FORECAST);
  });
});
