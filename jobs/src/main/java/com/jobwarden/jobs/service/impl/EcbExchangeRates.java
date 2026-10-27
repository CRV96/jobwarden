package com.jobwarden.jobs.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * The European Central Bank's reference rates, published every working day: downloaded when first needed, then kept
 * for a few hours. When they can't be downloaded, the last ones stand in, or else the approximate rates the app comes
 * with, and it tries again an hour later.
 */
@Component
@CustomLog
class EcbExchangeRates implements ExchangeRates {

	private static final URI DAILY_RATES = URI.create("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml");

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final Duration KEPT = Duration.ofHours(6);

	private static final Duration RETRY_AFTER = Duration.ofHours(1);

	/** In the bank's XML: {@code <Cube time='2026-10-23'>} for the day, {@code <Cube currency='USD' rate='1.1612'/>}. */
	private static final Pattern DAY = Pattern.compile("time=['\"](\\d{4}-\\d{2}-\\d{2})['\"]");

	private static final Pattern RATE = Pattern.compile("currency=['\"]([A-Z]{3})['\"]\\s+rate=['\"](\\d+(?:\\.\\d+)?)['\"]");

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private @Nullable RateTable rates;

	private Instant renewAt = Instant.MIN;

	@Override
	public synchronized RateTable current() {
		if (this.rates != null && Instant.now().isBefore(this.renewAt)) {
			return this.rates;
		}
		try {
			this.rates = parse(download());
			this.renewAt = Instant.now().plus(KEPT);
			log.info("Loaded the ECB's exchange rates of {}", this.rates.date());
		} catch (IOException | IllegalArgumentException ex) {
			keepWhatThereIs(ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			keepWhatThereIs(ex);
		}
		return this.rates;
	}

	private void keepWhatThereIs(Exception ex) {
		log.warn("Couldn't load the ECB's exchange rates, using {}: {}",
				(this.rates != null) ? "the last ones" : "approximate ones", ex.toString());
		if (this.rates == null) {
			this.rates = RateTable.APPROXIMATE;
		}
		this.renewAt = Instant.now().plus(RETRY_AFTER);
	}

	private String download() throws IOException, InterruptedException {
		HttpResponse<String> response = this.http.send(HttpRequest.newBuilder(DAILY_RATES).timeout(TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("The ECB answered " + response.statusCode());
		}
		return response.body();
	}

	/**
	 * @throws IllegalArgumentException if the XML has no day or no rates
	 */
	static RateTable parse(String xml) {
		Matcher day = DAY.matcher(xml);
		if (!day.find()) {
			throw new IllegalArgumentException("No day in the ECB's rates");
		}
		Map<String, BigDecimal> perEuro = new HashMap<>();
		Matcher rate = RATE.matcher(xml);
		while (rate.find()) {
			perEuro.put(rate.group(1), new BigDecimal(rate.group(2)));
		}
		if (perEuro.isEmpty()) {
			throw new IllegalArgumentException("No rates in the ECB's rates");
		}
		return new RateTable(perEuro, LocalDate.parse(day.group(1)));
	}

}
