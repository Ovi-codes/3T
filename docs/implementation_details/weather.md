# Weather

Next-run forecast on the events hero (Increment 9, #40)
Follows the same seam pattern as `EmailSender`/`AuthProvider`.

- **Seam:** everything above `ro.threet.run.weather.WeatherProvider` deals in a `Forecast` (a coordinate
  + the event's local date-time in, an `Optional<Forecast>` out). The provider reads Open-Meteo's
  **hourly** forecast and returns the reading at the event's (nearest) hour — one `temperatureC`
  (whole degrees, rounded upstream) and the event-hour `precipitationProbability`, not a daily spread.
  The only implementation is `OpenMeteoWeatherProvider` (Open-Meteo, no key, EU-hosted), wrapped by
  `CachingWeatherProvider`; both are assembled in `WeatherConfig` from env vars
- **Endpoint:** `GET /api/events/{id}/forecast`
- **Horizon:** `WEATHER_FORECAST_HORIZON_DAYS` (default **8**). Open-Meteo reaches ~16 days but
  the default is capped below that for better accuracy.
- **Cache:** Caffeine, `expireAfterWrite(WEATHER_CACHE_TTL)`. **TTL = the upstream refresh rate**
  A `Location` carries nullable `latitude`/`longitude` (V6 migration; Bucharest is seeded).
