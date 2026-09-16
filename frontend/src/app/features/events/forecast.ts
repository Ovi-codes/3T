import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, Observable, of, switchMap } from 'rxjs';

import { Forecast, FORECAST_UNAVAILABLE, ForecastService } from './forecast.service';

/** Icon keys the template draws; every WeatherCondition maps onto one of these. */
type IconKey = 'sun' | 'cloud-sun' | 'cloud' | 'fog' | 'rain' | 'snow' | 'storm';

/**
 * The next run's weather, shown on the events hero. It loads its own forecast for the given event and
 * renders nothing until one is available. On any error it degrades to "unavailable" and simply hides.
 */
@Component({
  selector: 'app-forecast',
  templateUrl: './forecast.html',
  styleUrl: './forecast.css',
})
export class ForecastWidget {
  private readonly forecasts = inject(ForecastService);

  /** The event to forecast — the soonest upcoming run*/
  readonly eventId = input.required<number>();

  /** The loaded forecast, or null while the first request is in flight. Errors become "unavailable". */
  protected readonly forecast = toSignal(
    toObservable(this.eventId).pipe(
      switchMap((id): Observable<Forecast | null> =>
        this.forecasts.forEvent(id).pipe(catchError(() => of(FORECAST_UNAVAILABLE))),
      ),
    ),
    { initialValue: null },
  );

  /** The icon to draw for the current condition. */
  protected readonly icon = computed<IconKey>(() => iconFor(this.forecast()?.condition));

  /** Whole-degree temperature for display (the hero shows no decimals). */
  protected round(value: number | null): string {
    return value === null ? '' : Math.round(value).toString();
  }
}

/** Collapse a condition name onto one of the drawn icons; unknown/absent falls back to a cloud. */
function iconFor(condition: string | null | undefined): IconKey {
  switch (condition) {
    case 'clear':
      return 'sun';
    case 'partly_cloudy':
      return 'cloud-sun';
    case 'fog':
      return 'fog';
    case 'drizzle':
    case 'rain':
      return 'rain';
    case 'snow':
      return 'snow';
    case 'thunderstorm':
      return 'storm';
    default:
      return 'cloud-sun';
  }
}
