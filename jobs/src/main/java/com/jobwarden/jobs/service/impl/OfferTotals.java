package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.dto.OfferTotal;
import org.jspecify.annotations.Nullable;

/**
 * Counts what an offer pays, all in, in one currency: a year of the base salary, and of each extra paid again and
 * again, converted at the salaries page's exchange rates; what's paid once, or per event, beside it.
 */
final class OfferTotals {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private OfferTotals() {
	}

	/**
	 * @param baseYearly a year of the base salary, in the currency; {@code null} without one
	 * @return the total, or nothing when there's nothing to count: no base salary, and no extra with an amount
	 */
	static Optional<OfferTotal> total(List<OfferExtra> extras, @Nullable BigDecimal baseYearly, String currency,
			RateTable rates) {
		BigDecimal recurring = BigDecimal.ZERO;
		BigDecimal once = BigDecimal.ZERO;
		BigDecimal perEvent = BigDecimal.ZERO;
		int uncounted = 0;
		boolean counted = baseYearly != null;
		for (OfferExtra extra : extras) {
			Optional<BigDecimal> payment = payment(extra, baseYearly, currency, rates);
			if (payment.isEmpty()) {
				uncounted++;
				continue;
			}
			counted = true;
			switch (extra.frequency()) {
				case ONCE -> once = once.add(payment.get());
				case EVENT -> perEvent = perEvent.add(payment.get());
				default -> recurring = recurring.add(payment.get().multiply(BigDecimal.valueOf(extra.frequency().perYear())));
			}
		}
		if (!counted) {
			return Optional.empty();
		}
		BigDecimal base = (baseYearly != null) ? rounded(baseYearly) : null;
		BigDecimal extrasYearly = rounded(recurring);
		return Optional.of(new OfferTotal(currency, base, extrasYearly,
				Objects.requireNonNullElse(base, BigDecimal.ZERO).add(extrasYearly), rounded(once), rounded(perEvent),
				uncounted));
	}

	/**
	 * One payment of the extra, in the currency: a share of the base salary is of a year of it, or of the part of the
	 * year one payment is for (a month of it, for a monthly one).
	 */
	private static Optional<BigDecimal> payment(OfferExtra extra, @Nullable BigDecimal baseYearly, String currency,
			RateTable rates) {
		BigDecimal amount = extra.amount();
		String unit = extra.unit();
		if (amount == null || unit == null) {
			return Optional.empty();
		}
		if (extra.isShareOfBase()) {
			if (baseYearly == null) {
				return Optional.empty();
			}
			BigDecimal share = baseYearly.multiply(amount).divide(HUNDRED, MathContext.DECIMAL64);
			return Optional.of(extra.frequency().isRecurring()
					? share.divide(BigDecimal.valueOf(extra.frequency().perYear()), MathContext.DECIMAL64) : share);
		}
		return converted(amount, unit, currency, rates);
	}

	/** In the other currency, when there's a rate for both, or it's the same one. */
	static Optional<BigDecimal> converted(BigDecimal amount, String from, String to, RateTable rates) {
		if (from.equals(to)) {
			return Optional.of(amount);
		}
		return (rates.currencies().contains(from) && rates.currencies().contains(to))
				? Optional.of(rates.convert(amount, from, to)) : Optional.empty();
	}

	private static BigDecimal rounded(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

}
