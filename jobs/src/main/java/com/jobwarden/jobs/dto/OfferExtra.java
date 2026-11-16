package com.jobwarden.jobs.dto;

import java.math.BigDecimal;

import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import org.jspecify.annotations.Nullable;

/**
 * Something an offer pays on top of its base salary, e.g. a yearly bonus of 10% of the base, meal vouchers of 40 RON a
 * working day, or shares worth 5,000 EUR a year.
 *
 * @param amount what one payment is; {@code null} when only the note says (e.g. "0.05%, vesting over 4 years")
 * @param unit with an amount: the ISO 4217 code of its currency, or {@link #SHARE_OF_BASE} for a percentage of the base
 * salary
 * @param note what it's for, or where from: on hitting targets, at Christmas, from the state...
 */
public record OfferExtra(OfferExtraKind kind, @Nullable BigDecimal amount, @Nullable String unit, PayFrequency frequency,
		@Nullable String note) {

	/** The unit of an amount that's a percentage of the base salary. */
	public static final String SHARE_OF_BASE = "%";

	/**
	 * @return whether the amount is a percentage of the base salary: of a year of it, or of a month for a monthly one
	 */
	public boolean isShareOfBase() {
		return SHARE_OF_BASE.equals(this.unit);
	}

}
