package com.jobwarden.market.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jobwarden.jobs.enums.SalaryPeriod;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What a position pays across countries, every figure in one currency and per one period.
 *
 * @param countries the countries asked for, in their order, then every country together when the survey has it
 * @param adzunaSearch what the job ads were searched for, e.g. {@code java developer}
 * @param adzunaConfigured whether the app has a key for Adzuna's API; without one there are no job ads' salaries
 * @param eurostatYear the year of Eurostat's figures, or {@code null} when they couldn't be loaded
 * @param euAverage Eurostat's figure for the 27 countries of the European Union together
 */
@Builder
public record MarketSalaries(@Nullable String role, @Nullable String skill, String currency, SalaryPeriod period,
		List<MarketCountry> countries, SurveyStatus survey, String adzunaSearch, boolean adzunaConfigured,
		@Nullable Integer eurostatYear, @Nullable BigDecimal euAverage) {

	public MarketSalaries {
		countries = List.copyOf(countries);
	}

	/**
	 * @return the highest figure of all, for the chart's axis
	 */
	public BigDecimal highest() {
		return this.countries.stream()
			.flatMap(country -> country.figures().stream())
			.map(MarketFigure::highest)
			.max(BigDecimal::compareTo)
			.orElse(BigDecimal.ZERO);
	}

	public boolean isEmpty() {
		return this.countries.stream().allMatch(country -> country.figures().isEmpty());
	}

}
