package com.jobwarden.jobs.service;

import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;

/**
 * Reads the salaries of a profile's jobs, which are text as the postings wrote them ("80,000–160,000 USD per year",
 * "15 000 zł brutto / mies."), and compares them.
 */
public interface SalaryService {

	/**
	 * @param currency the currency to show the salaries in, as an ISO 4217 code; {@code null}, or one there's no
	 * exchange rate for, for the one most of the salaries are in
	 */
	SalaryOverview overview(long userId, @Nullable String currency, SalaryPeriod period);

}
