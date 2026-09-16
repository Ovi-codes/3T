package ro.threet.run.weather;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/**
 * Caches forecasts in front of a {@link WeatherProvider} so repeated views of the same run don't
 * call the upstream API each time, better perf and staying a good Open-Meteo citizen.
 *
 * <p><b>TTL = the upstream refresh rate.</b>
 * The window is configured in {@link WeatherConfig} ({@code WEATHER_CACHE_TTL}).
 *
 * <p>Only present forecasts are cached. An empty result (a date past the horizon, or an upstream
 * failure) is not, so the moment upstream recovers the next view gets a real forecast rather than a
 * cached gap.
 */
class CachingWeatherProvider implements WeatherProvider {

	private record Key(double latitude, double longitude, LocalDate date) {
	}

	private final WeatherProvider delegate;
	private final Cache<Key, Forecast> cache;

	CachingWeatherProvider(WeatherProvider delegate, Duration ttl) {
		this(delegate, ttl, Ticker.systemTicker());
	}

	/** Test seam: a fake {@link Ticker} makes TTL expiry deterministic without real waiting. */
	CachingWeatherProvider(WeatherProvider delegate, Duration ttl, Ticker ticker) {
		this.delegate = delegate;
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(ttl)
				.ticker(ticker)
				.build();
	}

	@Override
	public Optional<Forecast> forecast(double latitude, double longitude, LocalDate date) {
		Key key = new Key(latitude, longitude, date);
		Forecast cached = cache.getIfPresent(key);
		if (cached != null) {
			return Optional.of(cached);
		}
		Optional<Forecast> fresh = delegate.forecast(latitude, longitude, date);
		fresh.ifPresent(forecast -> cache.put(key, forecast));
		return fresh;
	}

}
