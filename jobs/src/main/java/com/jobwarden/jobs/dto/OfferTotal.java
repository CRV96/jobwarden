package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;

/**
 * What an offer pays, all in, in one currency: a year of its base salary and of the extras paid again and again, and,
 * beside it, what's paid once and what's paid each time something happens, which a year can't count.
 *
 * @param currency the ISO 4217 code it's all in
 * @param base a year of the base salary; {@code null} without one
 * @param extras a year of the extras paid again and again
 * @param yearly the base salary and those extras: what a year pays
 * @param once what's paid once, e.g. a signing bonus
 * @param perEvent what's paid each time something happens, how often no one knows
 * @param uncounted how many extras it couldn't count: no amount, a currency there's no exchange rate for, or a share
 * of a base salary there isn't
 */
public record OfferTotal(String currency, @Nullable BigDecimal base, BigDecimal extras, BigDecimal yearly, BigDecimal once,
		BigDecimal perEvent, int uncounted) {

	/**
	 * @return what it pays per the period, as the offers page compares them: a twelfth of a year for a month
	 */
	public BigDecimal per(SalaryPeriod period) {
		return this.yearly.divide(BigDecimal.valueOf(period.perYear()), 2, RoundingMode.HALF_UP);
	}

	/**
	 * @return whether it counts more than the base salary: there's something on top
	 */
	public boolean hasExtras() {
		return this.extras.signum() > 0 || this.once.signum() > 0 || this.perEvent.signum() > 0;
	}

}
