package com.jobwarden.jobs.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * Converts amounts between currencies with the European Central Bank's daily reference rates, the same the salaries
 * page uses: downloaded when needed and kept for a few hours, approximate built-in rates standing in when they can't
 * be loaded.
 */
public interface ExchangeRateService {

	/**
	 * @return whether there's a rate for the currency, an ISO 4217 code
	 */
	boolean knows(String currency);

	/**
	 * @throws IllegalArgumentException if either currency has no rate
	 */
	BigDecimal convert(BigDecimal amount, String from, String to);

	/**
	 * @return the day of the rates, or {@code null} when approximate ones stand in
	 */
	@Nullable LocalDate ratesDate();

}
