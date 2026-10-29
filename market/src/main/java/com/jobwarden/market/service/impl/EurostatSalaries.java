package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Eurostat's Structure of Earnings Survey: the mean gross annual earnings, in euros, of professionals (ISCO-08 major
 * group 2) working in information and communication (NACE section J), in companies of ten people or more, for each
 * country of the European Economic Area and a few more. One request, in the statistics API's JSON-stat, for every
 * country; kept for a day, and a failure for an hour.
 */
@Component
@CustomLog
class EurostatSalaries {

	static final String CURRENCY = "EUR";

	/** The figure for the 27 countries of the European Union together. */
	private static final String EU = "EU27_2020";

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final Duration KEPT = Duration.ofDays(1);

	private static final Duration RETRY_AFTER = Duration.ofHours(1);

	private final URI address;

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private @Nullable EarningsTable table;

	private Instant renewAt = Instant.MIN;

	EurostatSalaries(@Value("${jobwarden.market.eurostat.url}") URI address) {
		this.address = address;
	}

	/**
	 * @return the earnings, or empty when they couldn't be loaded
	 */
	synchronized Optional<EarningsTable> current() {
		if (Instant.now().isBefore(this.renewAt)) {
			return Optional.ofNullable(this.table);
		}
		try {
			this.table = parse(this.json.readTree(download()));
			this.renewAt = Instant.now().plus(KEPT);
			log.info("Loaded Eurostat's earnings of {} for {} countries", this.table.year(), this.table.byCountry().size());
		} catch (IOException | JacksonException | IllegalArgumentException ex) {
			log.warn("Couldn't load Eurostat's earnings: {}", ex.toString());
			this.renewAt = Instant.now().plus(RETRY_AFTER);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return Optional.ofNullable(this.table);
	}

	private String download() throws IOException, InterruptedException {
		HttpResponse<String> response = this.http.send(HttpRequest.newBuilder(this.address).timeout(TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("Eurostat answered " + response.statusCode());
		}
		return response.body();
	}

	/**
	 * JSON-stat: {@code value} maps each cell's position to its figure, the cells in the order of the dimensions in
	 * {@code id}, each as long as {@code size} says. Every dimension but the country has one value here.
	 *
	 * @throws IllegalArgumentException if it isn't a table by country and year
	 */
	static EarningsTable parse(JsonNode answer) {
		JsonNode ids = answer.path("id");
		JsonNode sizes = answer.path("size");
		int geo = -1;
		for (int i = 0; i < ids.size(); i++) {
			if ("geo".equals(ids.get(i).asString())) {
				geo = i;
			}
		}
		if (geo < 0 || sizes.size() != ids.size()) {
			throw new IllegalArgumentException("Eurostat's answer has no countries");
		}
		int stride = 1;
		for (int i = geo + 1; i < sizes.size(); i++) {
			stride *= sizes.get(i).asInt(1);
		}
		JsonNode years = answer.path("dimension").path("time").path("category").path("index");
		if (years.size() != 1) {
			throw new IllegalArgumentException("Eurostat's answer isn't for one year");
		}
		int year = Integer.parseInt(years.propertyNames().iterator().next());
		Map<String, BigDecimal> byCountry = new HashMap<>();
		BigDecimal euAverage = null;
		JsonNode values = answer.path("value");
		for (Map.Entry<String, JsonNode> entry : answer.path("dimension").path("geo").path("category").path("index").properties()) {
			JsonNode value = values.get(String.valueOf(entry.getValue().asInt() * stride));
			if (value == null || !value.isNumber()) {
				continue;
			}
			BigDecimal amount = BigDecimal.valueOf(value.asDouble()).setScale(0, RoundingMode.HALF_UP);
			String country = Countries.ofEurostatCode(entry.getKey());
			if (country != null) {
				byCountry.put(country, amount);
			} else if (EU.equals(entry.getKey())) {
				euAverage = amount;
			}
		}
		return new EarningsTable(year, byCountry, euAverage);
	}

	/**
	 * Mean gross earnings for a year, in euros.
	 *
	 * @param byCountry by ISO 3166 two-letter code
	 * @param euAverage for the 27 countries of the European Union together
	 */
	record EarningsTable(int year, Map<String, BigDecimal> byCountry, @Nullable BigDecimal euAverage) {

		EarningsTable {
			byCountry = Map.copyOf(byCountry);
		}

	}

}
