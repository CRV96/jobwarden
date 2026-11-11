package com.jobwarden.jobs.dto;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * One offer, as the offers page compares it with the others.
 *
 * @param terms what the profile typed in about the offer; {@link JobOffer#NONE} when nothing yet
 * @param salaryText the salary the comparison read: the offer's base salary, or else the posting's; {@code null}
 * without either
 * @param salary that salary in the comparison's currency and period; {@code null} without one, or when it can't be read
 * @param salaryFromPosting whether it's the posting's salary, as the offer's terms give none
 * @param fitScore how well the profile fits the job, from 1 to 5, when Claude scored it
 * @param hasBrief whether Claude wrote a brief to negotiate it
 */
public record ComparedOffer(ApplicationSummary application, JobOffer terms, @Nullable String salaryText,
		@Nullable SalaryEntry salary, boolean salaryFromPosting, @Nullable BigDecimal fitScore, boolean hasBrief) {

	/**
	 * @return whether there's a salary that couldn't be read: no amount, or a currency there's no rate for
	 */
	public boolean salaryUnreadable() {
		return this.salaryText != null && this.salary == null;
	}

}
