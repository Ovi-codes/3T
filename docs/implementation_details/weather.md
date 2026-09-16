# Weather

Next-run forecast on the events hero (Increment 9, #40)
Follows the same seam pattern as `EmailSender`/`AuthProvider`.

- **Seam:** everything above `ro.threet.run.weather.WeatherProvider` deals in a `Forecast` (a coordinate
  + a date in, an `Optional<Forecast>` out). The only implementation is `OpenMeteoWeatherProvider`
  (Open-Meteo, no key, EU-hosted), wrapped by `CachingWeatherProvider`; both are assembled in
  `WeatherConfig` from env vars
- **Endpoint:** `GET /api/events/{id}/forecast`
- **Cache:** Caffeine, `expireAfterWrite(WEATHER_CACHE_TTL)`. **TTL = the upstream refresh rate**
  A `Location` carries nullable `latitude`/`longitude` (V6 migration; Bucharest is seeded).
