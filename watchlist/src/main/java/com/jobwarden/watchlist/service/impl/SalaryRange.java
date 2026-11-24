package com.jobwarden.watchlist.service.impl;

import java.math.BigDecimal;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * A feed's pay range as the AI Assistant page shows it, the same for each feed that has one (Lever's, Recruitee's,
 * Personio's): EUR 60,000–80,000 per year, USD 23.75–28.50 per hour.
 */
final class SalaryRange {

	/** More than any salary has, before the decimal point. */
	private static final int MAX_WHOLE_DIGITS = 12;

	/** As many as a double's shortest form has after the point (0.1 + 0.2 is 0.30000000000000004). */
	private static final int MAX_DECIMALS = 20;

	private SalaryRange() {
	}

	/**
	 * @param min a number, or a number written as text
	 * @param max the same
	 * @param interval how often it's paid, in words, e.g. per year
	 * @return {@code null} without an amount above zero
	 */
	static @Nullable String of(String currency, JsonNode min, JsonNode max, String interval) {
		BigDecimal from = amount(min);
		BigDecimal to = amount(max);
		if (from.signum() <= 0 && to.signum() <= 0) {
			return null;
		}
		String amount = (from.signum() > 0 && to.signum() > 0 && from.compareTo(to) != 0)
				? formatted(from) + "–" + formatted(to) : formatted(from.max(to));
		return (currency.strip() + " " + amount + " " + interval.strip()).strip();
	}

	/**
	 * The field's amount, a number or a number written as text; zero without one, or with one no salary could be. A
	 * board is anyone's to write: "1E+1000000000" is a short text, but writing it out, or rounding "1E-1000000000" to
	 * cents, would take a number of a billion digits. So the amount is checked before anything is done with it.
	 */
	private static BigDecimal amount(JsonNode value) {
		try {
			BigDecimal amount;
			if (value.isNumber()) {
				amount = value.decimalValue();
			} else if (value.isString()) {
				amount = new BigDecimal(value.asString().strip());
			} else {
				return BigDecimal.ZERO;
			}
			return isSalary(amount) ? amount : BigDecimal.ZERO;
		} catch (NumberFormatException | JacksonException _) {
			// Text that isn't a number, or a number a double can't hold (1e400), which Jackson refuses to convert
			return BigDecimal.ZERO;
		}
	}

	/**
	 * Up to a trillion, with no more decimals than a double writes: read without any arithmetic on the amount. The
	 * digits before the point are counted in a long: 1E+2147483647 has a scale of -2147483647, which an int would wrap.
	 */
	private static boolean isSalary(BigDecimal amount) {
		return (long) amount.precision() - amount.scale() <= MAX_WHOLE_DIGITS && amount.scale() <= MAX_DECIMALS;
	}

	/** With thousands separators, whatever the server's language, and cents only when it has them: 60,000 or 23.75. */
	private static String formatted(BigDecimal amount) {
		BigDecimal plain = amount.stripTrailingZeros();
		return (plain.scale() <= 0) ? String.format(Locale.ROOT, "%,d", plain.toBigInteger())
				: String.format(Locale.ROOT, "%,.2f", amount);
	}

}
