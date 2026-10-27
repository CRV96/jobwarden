package com.jobwarden.jobs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.enums.SalaryPeriod;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What a profile's jobs pay, all in one currency and per one period, so they compare.
 *
 * @param salaries the salaries that could be read, highest midpoint first
 * @param unreadable the applications whose salary is text without an amount and a currency that could be read
 * @param withoutSalary how many applications have no salary at all
 * @param median the median of the midpoints; {@code null} without salaries
 * @param lowerQuartile the midpoint a quarter of the salaries are below; {@code null} without salaries
 * @param upperQuartile the midpoint a quarter of the salaries are above; {@code null} without salaries
 * @param byStatus the median for each status with salaries, in the order of the statuses
 * @param currencies the currencies the overview can be in, in alphabetical order
 * @param ratesDate the day of the European Central Bank's exchange rates it used, or {@code null} when they couldn't
 * be loaded and approximate rates stood in
 */
@Builder
public record SalaryOverview(String currency, SalaryPeriod period, List<SalaryEntry> salaries,
		List<ApplicationSummary> unreadable, int withoutSalary, @Nullable BigDecimal median,
		@Nullable BigDecimal lowerQuartile, @Nullable BigDecimal upperQuartile, List<StatusSalary> byStatus,
		List<String> currencies, @Nullable LocalDate ratesDate) {

	public SalaryOverview {
		salaries = List.copyOf(salaries);
		unreadable = List.copyOf(unreadable);
		byStatus = List.copyOf(byStatus);
		currencies = List.copyOf(currencies);
	}

	/**
	 * @return how many applications the profile has
	 */
	public int total() {
		return this.salaries.size() + this.unreadable.size() + this.withoutSalary;
	}

	/**
	 * @return the salary with the highest upper end
	 */
	public Optional<SalaryEntry> highest() {
		return this.salaries.stream().max(Comparator.comparing(SalaryEntry::high));
	}

}
