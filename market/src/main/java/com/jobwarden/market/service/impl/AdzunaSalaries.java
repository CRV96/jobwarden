package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.service.impl.AdzunaKeys.Key;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * What current job ads pay, from the Adzuna API's salary histogram: how many ads, for a search in a country, give a
 * salary in each band. Needs the app's free {@code app_id} and {@code app_key} from developer.adzuna.com, saved in
 * Settings or in the environment ({@link AdzunaKeys}); without them it asks nothing. Answers are kept for a day, and a
 * failure for an hour, until the key changes.
 */
@Component
@CustomLog
class AdzunaSalaries {

	/** The countries Adzuna has job ads for, with the currency their salaries are in. */
	static final Map<String, String> CURRENCIES = Map.ofEntries(Map.entry("AT", "EUR"), Map.entry("AU", "AUD"),
			Map.entry("BE", "EUR"), Map.entry("BR", "BRL"), Map.entry("CA", "CAD"), Map.entry("CH", "CHF"),
			Map.entry("DE", "EUR"), Map.entry("ES", "EUR"), Map.entry("FR", "EUR"), Map.entry("GB", "GBP"),
			Map.entry("IN", "INR"), Map.entry("IT", "EUR"), Map.entry("MX", "MXN"), Map.entry("NL", "EUR"),
			Map.entry("NZ", "NZD"), Map.entry("PL", "PLN"), Map.entry("SG", "SGD"), Map.entry("US", "USD"),
			Map.entry("ZA", "ZAR"));

	/** Fewer ads with a salary than this say too little. */
	static final int MIN_ADS = 5;

	private static final Duration TIMEOUT = Duration.ofSeconds(8);

	private static final Duration KEPT = Duration.ofDays(1);

	private static final Duration RETRY_AFTER = Duration.ofHours(1);

	/** What {@link #check} searches for: there are ads for it in Great Britain at any time */
	private static final String CHECK_SEARCH = "developer";

	private final AdzunaKeys keys;

	private final URI api;

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	private final Map<String, Answer> answers = new ConcurrentHashMap<>();

	AdzunaSalaries(AdzunaKeys keys, @Value("${jobwarden.market.adzuna.url:https://api.adzuna.com/v1/api}") URI api) {
		this.keys = keys;
		this.api = api;
	}

	boolean configured() {
		return this.keys.current().isPresent();
	}

	boolean covers(String country) {
		return CURRENCIES.containsKey(country);
	}

	/**
	 * @param country a country Adzuna {@link #covers}
	 * @return the salaries of the ads, a year in the country's currency; empty without a key, with too few ads that
	 * give one, or when the API can't be reached
	 */
	Optional<AdsFigure> find(String country, String search) {
		Key appKey = this.keys.current().orElse(null);
		if (appKey == null || !covers(country)) {
			return Optional.empty();
		}
		String key = country + "|" + search.toLowerCase(Locale.ROOT);
		Answer kept = this.answers.get(key);
		if (kept != null && Instant.now().isBefore(kept.renewAt())) {
			return Optional.ofNullable(kept.figure());
		}
		try {
			HttpResponse<String> response = ask(appKey, country, search);
			if (response.statusCode() != 200) {
				// The address has the key: only the status goes in the log
				throw new IOException("Adzuna answered " + response.statusCode());
			}
			AdsFigure figure = fromHistogram(this.json.readTree(response.body()), CURRENCIES.get(country));
			this.answers.put(key, new Answer(figure, Instant.now().plus(KEPT)));
			return Optional.ofNullable(figure);
		} catch (IOException | JacksonException ex) {
			log.warn("Couldn't load Adzuna's salaries for {} in {}: {}", search, country, ex.toString());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		this.answers.put(key, new Answer(null, Instant.now().plus(RETRY_AFTER)));
		return Optional.empty();
	}

	/**
	 * Asks Adzuna with a key before it's saved. A key Adzuna doesn't know gets 401 or 403, or an AUTH_FAIL in the
	 * answer.
	 */
	AdzunaKeyCheck check(Key appKey) {
		try {
			HttpResponse<String> response = ask(appKey, "GB", CHECK_SEARCH);
			if (response.statusCode() == 200) {
				return AdzunaKeyCheck.ACCEPTED;
			}
			if (response.statusCode() == 401 || response.statusCode() == 403
					|| response.body().contains("AUTH_FAIL")) {
				return AdzunaKeyCheck.REFUSED;
			}
			log.warn("Couldn't check the Adzuna key with app ID {}: Adzuna answered {}", appKey.appId(),
					response.statusCode());
		} catch (IOException ex) {
			log.warn("Couldn't check the Adzuna key with app ID {}: {}", appKey.appId(), ex.toString());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return AdzunaKeyCheck.UNCHECKED;
	}

	/** The key changed: what was asked with the one before, failures included, is asked again. */
	void forgetAnswers() {
		this.answers.clear();
	}

	private HttpResponse<String> ask(Key appKey, String country, String search)
			throws IOException, InterruptedException {
		URI address = URI.create(this.api + "/jobs/" + country.toLowerCase(Locale.ROOT) + "/histogram?app_id="
				+ encode(appKey.appId()) + "&app_key=" + encode(appKey.appKey()) + "&what=" + encode(search));
		return this.http.send(
				HttpRequest.newBuilder(address).timeout(TIMEOUT).header("Accept", "application/json").GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * The histogram's bands start at each key and end where the next starts; the last is as wide as the one before.
	 * Within a band, the ads are taken as spread evenly.
	 *
	 * @return {@code null} with fewer than {@link #MIN_ADS} ads
	 */
	static @Nullable AdsFigure fromHistogram(JsonNode answer, String currency) {
		TreeMap<Double, Integer> bands = new TreeMap<>();
		for (Map.Entry<String, JsonNode> band : answer.path("histogram").properties()) {
			try {
				int ads = band.getValue().asInt(0);
				if (ads > 0) {
					bands.merge(Double.parseDouble(band.getKey()), ads, Integer::sum);
				}
			} catch (NumberFormatException notAnAmount) {
				// Not a band
			}
		}
		int total = bands.values().stream().mapToInt(Integer::intValue).sum();
		if (total < MIN_ADS) {
			return null;
		}
		List<Double> starts = List.copyOf(bands.keySet());
		return new AdsFigure(currency, total, at(bands, starts, total * 0.25), at(bands, starts, total * 0.5),
				at(bands, starts, total * 0.75));
	}

	private static BigDecimal at(TreeMap<Double, Integer> bands, List<Double> starts, double rank) {
		double before = 0;
		for (int i = 0; i < starts.size(); i++) {
			int ads = bands.get(starts.get(i));
			if (before + ads >= rank) {
				double width = (i + 1 < starts.size()) ? starts.get(i + 1) - starts.get(i)
						: (i > 0) ? starts.get(i) - starts.get(i - 1) : 0;
				double value = starts.get(i) + width * (rank - before) / ads;
				return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
			}
			before += ads;
		}
		return BigDecimal.valueOf(starts.getLast()).setScale(0, RoundingMode.HALF_UP);
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	/**
	 * What the job ads pay, a year in the country's currency.
	 *
	 * @param ads how many ads give a salary
	 */
	record AdsFigure(String currency, int ads, BigDecimal lowerQuartile, BigDecimal median, BigDecimal upperQuartile) {
	}

	private record Answer(@Nullable AdsFigure figure, Instant renewAt) {
	}

}
