package com.jobwarden.jobs.dto;

import java.math.BigDecimal;

import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A salary as amounts: what a salary's text says, or what the profile typed in for a job. Build it with
 * {@link #builder()}; a range given the wrong way round is put in order.
 *
 * @param low the lower end of the range; the salary itself if it has no range
 * @param high the upper end of the range; the salary itself if it has no range
 * @param currency the ISO 4217 code
 * @param period what it pays for; {@code null} when it doesn't say
 * @param basis before tax or after; {@code null} when it doesn't say
 */
@Builder
public record SalaryRange(BigDecimal low, BigDecimal high, String currency, @Nullable SalaryPeriod period,
		@Nullable PayBasis basis) {

	public SalaryRange {
		if (low.compareTo(high) > 0) {
			BigDecimal higher = low;
			low = high;
			high = higher;
		}
	}

	/**
	 * @return whether it says the same as the other: the same amounts, however they're written (5000 is 5000.00)
	 */
	public boolean sameAs(@Nullable SalaryRange other) {
		return other != null && this.low.compareTo(other.low) == 0 && this.high.compareTo(other.high) == 0
				&& this.currency.equals(other.currency) && this.period == other.period
				&& this.basis == other.basis;
	}

	/**
	 * @return whether it's a single amount rather than a range
	 */
	public boolean isSingleAmount() {
		return this.low.compareTo(this.high) == 0;
	}

}
