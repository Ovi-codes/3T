import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Shape of GET /api/events/{id}/forecast — mirrors the backend ForecastResponse. `available` is the
 * whole contract the UI needs: when false, every other field is null and the widget shows nothing.
 */
export interface Forecast {
  available: boolean;
  date: string | null;
  /** Lowercase condition name (e.g. "rain"), for picking an icon. Null when unavailable. */
  condition: string | null;
  description: string | null;
  /** Temperature at the event hour, whole °C. Null when unavailable or upstream had no value. */
  temperatureC: number | null;
  /** Chance of precipitation at the event hour, percent. Null when unavailable. */
  precipitationProbability: number | null;
}

/** A forecast with nothing to show — the graceful-degradation value (upstream down, or off-horizon). */
export const FORECAST_UNAVAILABLE: Forecast = {
  available: false,
  date: null,
  condition: null,
  description: null,
  temperatureC: null,
  precipitationProbability: null,
};

/**
 * Owns the forecast resource. The one home for the forecast API contract, so components hold view
 * state only and never build `/api` URLs themselves (mirrors EventsService).
 */
@Injectable({ providedIn: 'root' })
export class ForecastService {
  private readonly http = inject(HttpClient);

  /** The forecast for one event, by id. Callers degrade on error — the forecast is never load-critical. */
  forEvent(eventId: number): Observable<Forecast> {
    return this.http.get<Forecast>(`/api/events/${eventId}/forecast`);
  }
}
