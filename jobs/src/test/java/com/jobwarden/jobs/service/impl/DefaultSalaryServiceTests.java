package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DefaultSalaryServiceTests {

	/** Round rates, so the expected amounts are easy to check: 1 EUR = 2 USD = 5 RON. */
	private static final RateTable RATES = new RateTable(
			Map.of("USD", BigDecimal.valueOf(2), "RON", BigDecimal.valueOf(5)), LocalDate.of(2026, 10, 23));

	private final ApplicationService applications = mock(ApplicationService.class);

	private final DefaultSalaryService salaries = new DefaultSalaryService(this.applications, () -> RATES);

	@BeforeEach
	void jobs() {
		given(this.applications.findAll(1, null)).willReturn(List.of(
				application(1, "Risk Analyst", ApplicationStatus.APPLIED, "80,000–160,000 USD per year"),
				application(2, "Java Developer", ApplicationStatus.APPLIED, "5000 RON"),
				application(3, "Platform Engineer", ApplicationStatus.OFFER, "60.000 EUR pro Jahr"),
				application(4, "Designer", ApplicationStatus.SAVED, "Competitive"),
				application(5, "Tester", ApplicationStatus.SAVED, null)));
	}

	@Test
	void convertsEverySalaryToTheCurrencyAndPeriodAsked() {
		SalaryOverview overview = this.salaries.overview(1, "EUR", SalaryPeriod.YEAR);

		assertThat(overview.currency()).isEqualTo("EUR");
		// Highest midpoint first: 60,000 EUR; 40,000–80,000 EUR; 5000 RON a month is 1000 EUR, 12,000 a year
		assertThat(overview.salaries()).extracting(entry -> entry.application().id()).containsExactly(1L, 3L, 2L);
		SalaryEntry riskAnalyst = overview.salaries().getFirst();
		assertThat(riskAnalyst.low()).isEqualByComparingTo("40000");
		assertThat(riskAnalyst.high()).isEqualByComparingTo("80000");
		assertThat(riskAnalyst.currency()).isEqualTo("USD");
		assertThat(riskAnalyst.periodGuessed()).isFalse();
	}

	@Test
	void guessesThePeriodASalaryDoesNotSay() {
		SalaryEntry javaDeveloper = this.salaries.overview(1, "RON", SalaryPeriod.MONTH)
			.salaries()
			.stream()
			.filter(entry -> entry.application().id() == 2)
			.findFirst()
			.orElseThrow();

		// 5000 RON is 1000 EUR: a month, not a year
		assertThat(javaDeveloper.periodGuessed()).isTrue();
		assertThat(javaDeveloper.low()).isEqualByComparingTo("5000");
	}

	@Test
	void summarizesTheMidpoints() {
		SalaryOverview overview = this.salaries.overview(1, "EUR", SalaryPeriod.YEAR);

		// Midpoints 12,000, 60,000 and 60,000
		assertThat(overview.median()).isEqualByComparingTo("60000");
		assertThat(overview.lowerQuartile()).isEqualByComparingTo("36000");
		assertThat(overview.upperQuartile()).isEqualByComparingTo("60000");
		assertThat(overview.highest()).get().extracting(entry -> entry.application().id()).isEqualTo(1L);
		assertThat(overview.byStatus()).extracting(status -> status.status() + " " + status.median().intValue() + " "
				+ status.count())
			.containsExactly("APPLIED 36000 2", "OFFER 60000 1");
	}

	@Test
	void listsTheSalariesItCannotRead() {
		SalaryOverview overview = this.salaries.overview(1, "EUR", SalaryPeriod.YEAR);

		assertThat(overview.unreadable()).extracting(ApplicationSummary::title).containsExactly("Designer");
		assertThat(overview.withoutSalary()).isEqualTo(1);
		assertThat(overview.total()).isEqualTo(5);
	}

	@Test
	void showsTheMostCommonCurrencyByDefault() {
		given(this.applications.findAll(2, null)).willReturn(List.of(
				application(6, "A", ApplicationStatus.APPLIED, "5000 RON"),
				application(7, "B", ApplicationStatus.APPLIED, "6000 RON"),
				application(8, "C", ApplicationStatus.APPLIED, "$100,000")));

		SalaryOverview overview = this.salaries.overview(2, null, SalaryPeriod.MONTH);

		assertThat(overview.currency()).isEqualTo("RON");
		// GBP is always offered, but these rates have none for it
		assertThat(overview.currencies()).containsExactly("EUR", "RON", "USD");
		assertThat(overview.ratesDate()).isEqualTo(LocalDate.of(2026, 10, 23));
	}

	@Test
	void showsEurosWithoutSalaries() {
		given(this.applications.findAll(3, null)).willReturn(List.of());

		SalaryOverview overview = this.salaries.overview(3, "XYZ", SalaryPeriod.YEAR);

		assertThat(overview.currency()).isEqualTo("EUR");
		assertThat(overview.median()).isNull();
		assertThat(overview.highest()).isEmpty();
	}

	private static ApplicationSummary application(long id, String title, ApplicationStatus status,
			@Nullable String salary) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.status(status)
			.salary(salary)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.build();
	}

}
