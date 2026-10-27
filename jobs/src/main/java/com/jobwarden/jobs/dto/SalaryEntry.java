package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.jobwarden.jobs.enums.PayBasis;
import org.jspecify.annotations.Nullable;

/**
 * A job's salary, in the overview's currency and period.
 *
 * @param low the lower end of the range; the salary itself if it has no range
 * @param high the upper end of the range; the salary itself if it has no range
 * @param currency the currency the posting gave it in
 * @param periodGuessed whether the posting didn't say what the salary pays for, so it was guessed from the amount
 * @param basis gross or net, if the posting says
 */
public record SalaryEntry(ApplicationSummary application, BigDecimal low, BigDecimal high, String currency,
		boolean periodGuessed, @Nullable PayBasis basis) {

	/**
	 * @return the middle of the range: what the statistics count
	 */
	public BigDecimal midpoint() {
		return this.low.add(this.high).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
	}

	public boolean isRange() {
		return this.low.compareTo(this.high) != 0;
	}

}
