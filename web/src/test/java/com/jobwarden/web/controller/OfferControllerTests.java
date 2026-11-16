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
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.dto.OfferTotal;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketFigure;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.dto.SurveyStatus;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(OfferController.class)
class OfferControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final ApplicationSummary ACME = ApplicationSummary.builder()
		.id(7)
		.title("Java Developer")
		.company("Acme")
		.location("Berlin, Germany")
		.status(ApplicationStatus.OFFER)
		.capturedAt(Instant.now())
		.build();

	private static final ApplicationSummary GLOBEX = ApplicationSummary.builder()
		.id(8)
		.title("Backend Engineer")
		.company("Globex")
		.location("Remote")
		.salary("Competitive")
		.status(ApplicationStatus.OFFER)
		.capturedAt(Instant.now())
		.build();

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
	private OfferService offers;

	@MockitoBean
	private MarketSalaryService market;

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.market.roles()).willReturn(List.of("Developer, back-end"));
		given(this.market.skills()).willReturn(List.of("Java"));
	}

	@Test
	void putsTheOffersSideBySide() {
		given(this.offers.compare(1, null, SalaryPeriod.YEAR)).willReturn(comparison());

		assertThat(this.mvc.get().uri("/offers")).hasStatusOk()
			.bodyText()
			.contains("Java Developer", "Globex", "90,000 EUR gross per year", "€90K", "Highest base salary",
					// What it pays on top, and all in
					"On top of the salary", "10% of the base salary", "yearly", "On targets", "Meal vouchers", "€10",
					"per working day", "All in, a year", "€101K", "And €5K once", "25 days", "Can’t be read as an amount", "/applications/7/drawer", "Read the brief",
					"Ask the AI Assistant how to negotiate")
			// Answer due in two days
			.contains("2 days left")
			// The market row loads after the page, for the role and language picked
			.contains("/offers/market?currency=EUR&amp;period=YEAR&amp;role=Developer,%20back-end&amp;skill=Java");
	}

	@Test
	void saysWhenThereAreNoOffers() {
		given(this.offers.compare(1, null, SalaryPeriod.YEAR)).willReturn(OfferComparison.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.offers(List.of())
			.currencies(List.of("EUR"))
			.build());

		assertThat(this.mvc.get().uri("/offers")).hasStatusOk().bodyText().contains("No offers yet").doesNotContain("<table");
	}

	@Test
	void setsEachOfferBesideWhatTheMarketPaysInItsCountry() {
		given(this.offers.compare(1, "EUR", SalaryPeriod.YEAR)).willReturn(comparison());
		given(this.market.find(new MarketQuery("Developer, back-end", "Java", List.of("DE")), "EUR", SalaryPeriod.YEAR))
			.willReturn(MarketSalaries.builder()
				.currency("EUR")
				.period(SalaryPeriod.YEAR)
				.countries(List.of(new MarketCountry("DE", List.of(new MarketFigure(MarketSource.STACK_OVERFLOW, 2025,
						new BigDecimal("60000"), new BigDecimal("75000"), new BigDecimal("88000"), 300)))))
				.survey(new SurveyStatus(2025, 1000, Instant.now(), false, null))
				.adzunaSearch("java developer")
				.build());

		assertThat(this.mvc.get()
			.uri("/offers/market")
			.param("currency", "EUR")
			.param("period", "YEAR")
			.param("role", "Developer, back-end")
			.param("skill", "Java")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("offers-market", "Germany", "€75K", "+20% on the market", "Over the middle half", "Stack Overflow survey 2025")
			// The job open to the world has no country to compare with
			.contains("No single country to compare with");
	}

	private static OfferComparison comparison() {
		return OfferComparison.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.offers(List.of(
					new ComparedOffer(ACME, JobOffer.builder()
						.baseSalary("90,000 EUR gross per year")
						.extras(List.of(new OfferExtra(OfferExtraKind.BONUS, BigDecimal.TEN, "%", PayFrequency.YEAR, "On targets"),
								new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, BigDecimal.TEN, "EUR", PayFrequency.WORKING_DAY, null)))
						.timeOff("25 days")
						.respondBy(LocalDate.now().plusDays(2))
						.build(), "90,000 EUR gross per year",
							new SalaryEntry(ACME, new BigDecimal("90000"), new BigDecimal("90000"), "EUR", false, null), false,
							new OfferTotal("EUR", new BigDecimal("90000"), new BigDecimal("11200"), new BigDecimal("101200"),
									new BigDecimal("5000"), BigDecimal.ZERO, 0),
							new BigDecimal("4.2"), true),
					new ComparedOffer(GLOBEX, JobOffer.NONE, "Competitive", null, true, null, null, false),
					new ComparedOffer(ApplicationSummary.builder()
						.id(9)
						.title("Platform Engineer")
						.company("Initech")
						.status(ApplicationStatus.OFFER)
						.capturedAt(Instant.now())
						.build(), JobOffer.NONE, "70,000 EUR", new SalaryEntry(ACME, new BigDecimal("70000"),
								new BigDecimal("70000"), "EUR", false, null), true, null, null, false)))
			.currencies(List.of("EUR", "USD"))
			.build();
	}

}
