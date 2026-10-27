package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.StatusSalary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.PayBasis;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.SalaryService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(SalaryController.class)
class SalaryControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private SalaryService salaries;

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
	}

	@Test
	void comparesTheSalaries() {
		given(this.salaries.overview(1, null, SalaryPeriod.YEAR)).willReturn(overview());

		assertThat(this.mvc.get().uri("/salaries")).hasStatusOk()
			.bodyText()
			.contains("€60K", "€40K – €80K", "80,000–160,000 USD per year", "2 of 4", "Risk Analyst",
					"Engineer", ">net<", "26 Oct 2026", "Designer", "value=\"Competitive\"")
			// The axis, and the medians by status
			.contains("€80K", "status-bar");
	}

	@Test
	void showsThePeriodAndCurrencyAsked() {
		given(this.salaries.overview(1, "RON", SalaryPeriod.MONTH)).willReturn(overview());

		assertThat(this.mvc.get().uri("/salaries").param("currency", "RON").param("period", "MONTH")).hasStatusOk();
	}

	@Test
	void saysWhenThereAreNoSalaries() {
		given(this.salaries.overview(1, null, SalaryPeriod.YEAR)).willReturn(SalaryOverview.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.salaries(List.of())
			.unreadable(List.of())
			.withoutSalary(3)
			.byStatus(List.of())
			.currencies(List.of("EUR"))
			.build());

		assertThat(this.mvc.get().uri("/salaries")).hasStatusOk()
			.bodyText()
			.contains("No salaries yet")
			.doesNotContain("Median", "salary-chart");
	}

	@Test
	void needsAProfile() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/salaries")).hasStatus3xxRedirection().hasRedirectedUrl("/profiles");
	}

	private static SalaryOverview overview() {
		SalaryEntry analyst = new SalaryEntry(application(1, "Risk Analyst", ApplicationStatus.APPLIED,
				"80,000–160,000 USD per year"), new BigDecimal("40000"), new BigDecimal("80000"), "USD", false, null);
		SalaryEntry engineer = new SalaryEntry(application(2, "Engineer", ApplicationStatus.OFFER, "60.000 EUR netto"),
				new BigDecimal("60000"), new BigDecimal("60000"), "EUR", true, PayBasis.NET);
		return SalaryOverview.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.salaries(List.of(analyst, engineer))
			.unreadable(List.of(application(3, "Designer", ApplicationStatus.SAVED, "Competitive")))
			.withoutSalary(1)
			.median(new BigDecimal("60000"))
			.lowerQuartile(new BigDecimal("60000"))
			.upperQuartile(new BigDecimal("60000"))
			.byStatus(List.of(new StatusSalary(ApplicationStatus.APPLIED, new BigDecimal("60000"), 1),
					new StatusSalary(ApplicationStatus.OFFER, new BigDecimal("60000"), 1)))
			.currencies(List.of("EUR", "USD"))
			.ratesDate(LocalDate.of(2026, 10, 26))
			.build();
	}

	private static ApplicationSummary application(long id, String title, ApplicationStatus status, String salary) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.status(status)
			.salary(salary)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.build();
	}

}
