package com.jobwarden.jobs.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.SalaryPeriod;
import org.jspecify.annotations.Nullable;

/**
 * Reads the salaries of a profile's jobs, which are text as the postings wrote them ("80,000–160,000 USD per year",
 * "15 000 zł brutto / mies."), and compares them.
 */
public interface SalaryService {

	/**
	 * @param currency the currency to show the salaries in, as an ISO 4217 code; {@code null}, or one there's no
	 * exchange rate for, for euros
	 */
	SalaryOverview overview(long userId, @Nullable String currency, SalaryPeriod period);

	/**
	 * @param salary a job's salary, as the posting wrote it
	 * @return what it says, or nothing without an amount and a currency there's an exchange rate for
	 */
	Optional<SalaryRange> read(@Nullable String salary);

	/**
	 * @return the currencies a salary can be compared in: those there are exchange rates for, in alphabetical order
	 */
	List<String> currencies();

	/**
	 * Gives the job the salary the profile typed in, written in its language as postings write salaries
	 * ("60,000–80,000 EUR gross per year"), so it's compared like the others. The job keeps the posting's own text
	 * when that says the same, or when there's no range and the text can't be read as one: only what it says changes.
	 *
	 * @param range {@code null} for no salary
	 * @param locale the profile's language, which the period and the tax are written in
	 */
	void change(long userId, long applicationId, @Nullable SalaryRange range, Locale locale);

}
