package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Exchange rates: how much of each currency one euro buys, with the euro itself at 1.
 *
 * @param date the day the rates are for; {@code null} for the approximate ones the app comes with
 */
record RateTable(Map<String, BigDecimal> perEuro, @Nullable LocalDate date) {

	/**
	 * What the European Central Bank's rates were about in October 2026: they stand in when the bank's can't be loaded.
	 */
	static final RateTable APPROXIMATE = new RateTable(Map.ofEntries(Map.entry("USD", new BigDecimal("1.16")),
			Map.entry("GBP", new BigDecimal("0.87")), Map.entry("CHF", new BigDecimal("0.93")),
			Map.entry("PLN", new BigDecimal("4.25")), Map.entry("RON", new BigDecimal("5.08")),
			Map.entry("CZK", new BigDecimal("24.4")), Map.entry("HUF", new BigDecimal("390")),
			Map.entry("SEK", new BigDecimal("11.0")), Map.entry("NOK", new BigDecimal("11.7")),
			Map.entry("DKK", new BigDecimal("7.46")), Map.entry("ISK", new BigDecimal("143")),
			Map.entry("TRY", new BigDecimal("48.5")), Map.entry("CAD", new BigDecimal("1.62")),
			Map.entry("AUD", new BigDecimal("1.77")), Map.entry("NZD", new BigDecimal("2.0")),
			Map.entry("JPY", new BigDecimal("172")), Map.entry("CNY", new BigDecimal("8.3")),
			Map.entry("HKD", new BigDecimal("9.05")), Map.entry("SGD", new BigDecimal("1.5")),
			Map.entry("KRW", new BigDecimal("1630")), Map.entry("INR", new BigDecimal("102")),
			Map.entry("ILS", new BigDecimal("3.9")), Map.entry("BRL", new BigDecimal("6.25")),
			Map.entry("MXN", new BigDecimal("21.5")), Map.entry("ZAR", new BigDecimal("20.3")),
			Map.entry("IDR", new BigDecimal("19000")), Map.entry("MYR", new BigDecimal("4.9")),
			Map.entry("PHP", new BigDecimal("66")), Map.entry("THB", new BigDecimal("37.5"))), null);

	RateTable {
		Map<String, BigDecimal> withEuro = new HashMap<>(perEuro);
		withEuro.put("EUR", BigDecimal.ONE);
		perEuro = Map.copyOf(withEuro);
	}

	Set<String> currencies() {
		return this.perEuro.keySet();
	}

	/**
	 * @param from a currency with a rate
	 * @param to a currency with a rate
	 */
	BigDecimal convert(BigDecimal amount, String from, String to) {
		return amount.divide(this.perEuro.get(from), MathContext.DECIMAL64).multiply(this.perEuro.get(to), MathContext.DECIMAL64);
	}

}
