package com.jobwarden.jobs.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class DefaultSalaryServiceTests {

	/** Round rates, so the expected amounts are easy to check: 1 EUR = 2 USD = 5 RON. */
	private static final RateTable RATES = new RateTable(
			Map.of("USD", BigDecimal.valueOf(2), "RON", BigDecimal.valueOf(5)), LocalDate.of(2026, 10, 23));

	private final ApplicationService applications = mock(ApplicationService.class);

	private final DefaultSalaryService salaries = new DefaultSalaryService(this.applications, () -> RATES, messages());

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
	void showsEurosByDefault() {
		given(this.applications.findAll(2, null)).willReturn(List.of(
				application(6, "A", ApplicationStatus.APPLIED, "5000 RON"),
				application(7, "B", ApplicationStatus.APPLIED, "6000 RON"),
				application(8, "C", ApplicationStatus.APPLIED, "$100,000")));

		SalaryOverview overview = this.salaries.overview(2, null, SalaryPeriod.MONTH);

		// Even when most of the salaries are in another currency
		assertThat(overview.currency()).isEqualTo("EUR");
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

	/** Every language the messages are in, with every period and tax: the salaries page reads what's written. */
	@ParameterizedTest
	@ValueSource(strings = { "en", "de", "es", "fr", "pl", "pt", "ro" })
	void writesATypedInSalarySoItIsReadBack(String language) {
		Locale locale = Locale.forLanguageTag(language);
		for (SalaryPeriod period : SalaryPeriod.values()) {
			for (PayBasis basis : PayBasis.values()) {
				SalaryRange range = range("60000", "80500.5", "RON", period, basis);
				String text = this.salaries.text(range, locale);
				assertThat(this.salaries.read(text)).as(text).get().matches(range::sameAs, "the same as " + range);
			}
		}
		SalaryRange single = range("37.5", "37.5", "USD", null, null);
		assertThat(this.salaries.read(this.salaries.text(single, locale))).get().matches(single::sameAs);
	}

	@Test
	void writesASalaryAsPostingsDo() {
		assertThat(this.salaries.text(range("60000", "80000", "EUR", SalaryPeriod.YEAR, PayBasis.GROSS), Locale.ENGLISH))
			.isEqualTo("60,000–80,000 EUR gross per year");
		assertThat(this.salaries.text(range("6000", "6000", "RON", SalaryPeriod.MONTH, PayBasis.NET),
				Locale.forLanguageTag("ro")))
			.isEqualTo("6.000 RON net pe lună");
		assertThat(this.salaries.text(range("45.5", "45.5", "USD", null, null), Locale.GERMAN)).isEqualTo("45,5 USD");
	}

	@Test
	void changesTheSalaryToTheOneTypedIn() {
		job(9, "Competitive");

		this.salaries.change(1, 9, range("80000", "60000", "EUR", SalaryPeriod.YEAR, PayBasis.GROSS), Locale.ENGLISH);

		then(this.applications).should().updateSalary(1, 9, "60,000–80,000 EUR gross per year");
	}

	@Test
	void keepsThePostingsTextWhenItSaysTheSame() {
		job(9, "$120K - $150K a year");

		this.salaries.change(1, 9, range("120000", "150000.00", "USD", SalaryPeriod.YEAR, null), Locale.ENGLISH);

		then(this.applications).should(never()).updateSalary(anyLong(), anyLong(), any());
	}

	@Test
	void keepsATextItCannotReadWithoutARangeToReplaceIt() {
		job(9, "Competitive");

		this.salaries.change(1, 9, null, Locale.ENGLISH);

		then(this.applications).should(never()).updateSalary(anyLong(), anyLong(), any());
	}

	@Test
	void clearsASalaryItCouldRead() {
		job(9, "5000 RON");

		this.salaries.change(1, 9, null, Locale.ENGLISH);

		then(this.applications).should().updateSalary(1, 9, null);
	}

	@Test
	void changesNothingOfAJobTheProfileDoesNotHave() {
		given(this.applications.findById(1, 10)).willReturn(Optional.empty());

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.salaries.change(1, 10, null, Locale.ENGLISH));
	}

	@Test
	void offersTheCurrenciesThereAreRatesFor() {
		assertThat(this.salaries.currencies()).containsExactly("EUR", "RON", "USD");
	}

	private void job(long id, @Nullable String salary) {
		given(this.applications.findById(1, id)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(id)
			.title("Backend engineer")
			.salary(salary)
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.timeline(List.of())
			.build()));
	}

	private static SalaryRange range(String low, String high, String currency, @Nullable SalaryPeriod period,
			@Nullable PayBasis basis) {
		return new SalaryRange(new BigDecimal(low), new BigDecimal(high), currency, period, basis);
	}

	private static ResourceBundleMessageSource messages() {
		ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
		messages.setBasename("i18n/jobs-salaries");
		messages.setDefaultEncoding("UTF-8");
		messages.setFallbackToSystemLocale(false);
		return messages;
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
