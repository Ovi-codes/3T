package ro.threet.run.weather;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Ticker;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The caching decorator: a hit inside the TTL never reaches the delegate, the entry expires once the
 * TTL passes, empties aren't cached, and different coordinates/dates are independent. A fake
 * {@link Ticker} drives expiry, so the TTL behaviour is proven without real waiting.
 */
class CachingWeatherProviderTest {

	private static final double LAT = 44.41;
	private static final double LON = 26.1;
	private static final LocalDate DATE = LocalDate.parse("2026-09-18");
	private static final Duration TTL = Duration.ofHours(1);

	/** A hand-advanced clock for Caffeine, so "an hour later" is a method call, not a sleep. */
	private static final class FakeTicker implements Ticker {
		private long nanos;

		@Override
		public long read() {
			return nanos;
		}

		void advance(Duration duration) {
			nanos += duration.toNanos();
		}
	}

	private static Forecast sampleForecast() {
		return new Forecast(DATE, WeatherCondition.CLEAR, "Clear sky", 21.0, 10.0, 5);
	}

	@Test
	void secondViewInsideTheTtlIsServedFromCache() {
		WeatherProvider delegate = Mockito.mock(WeatherProvider.class);
		when(delegate.forecast(anyDouble(), anyDouble(), eq(DATE))).thenReturn(Optional.of(sampleForecast()));
		CachingWeatherProvider caching = new CachingWeatherProvider(delegate, TTL, new FakeTicker());

		Optional<Forecast> first = caching.forecast(LAT, LON, DATE);
		Optional<Forecast> second = caching.forecast(LAT, LON, DATE);

		assertThat(first).contains(sampleForecast());
		assertThat(second).contains(sampleForecast());
		verify(delegate, times(1)).forecast(anyDouble(), anyDouble(), eq(DATE));
	}

	@Test
	void theEntryExpiresOnceTheTtlPasses() {
		WeatherProvider delegate = Mockito.mock(WeatherProvider.class);
		when(delegate.forecast(anyDouble(), anyDouble(), eq(DATE))).thenReturn(Optional.of(sampleForecast()));
		FakeTicker ticker = new FakeTicker();
		CachingWeatherProvider caching = new CachingWeatherProvider(delegate, TTL, ticker);

		caching.forecast(LAT, LON, DATE);
		ticker.advance(TTL.plusMinutes(1)); // one refresh cycle later
		caching.forecast(LAT, LON, DATE);

		verify(delegate, times(2)).forecast(anyDouble(), anyDouble(), eq(DATE));
	}

	@Test
	void emptyResultsAreNotCachedSoRecoveryIsImmediate() {
		WeatherProvider delegate = Mockito.mock(WeatherProvider.class);
		when(delegate.forecast(anyDouble(), anyDouble(), eq(DATE)))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(sampleForecast()));
		CachingWeatherProvider caching = new CachingWeatherProvider(delegate, TTL, new FakeTicker());

		assertThat(caching.forecast(LAT, LON, DATE)).isEmpty();
		assertThat(caching.forecast(LAT, LON, DATE)).contains(sampleForecast());
		verify(delegate, times(2)).forecast(anyDouble(), anyDouble(), eq(DATE));
	}

	@Test
	void differentCoordinatesDoNotShareACacheEntry() {
		WeatherProvider delegate = Mockito.mock(WeatherProvider.class);
		when(delegate.forecast(anyDouble(), anyDouble(), eq(DATE))).thenReturn(Optional.of(sampleForecast()));
		CachingWeatherProvider caching = new CachingWeatherProvider(delegate, TTL, new FakeTicker());

		caching.forecast(LAT, LON, DATE);
		caching.forecast(45.0, 25.0, DATE);

		verify(delegate, times(2)).forecast(anyDouble(), anyDouble(), eq(DATE));
	}

}
