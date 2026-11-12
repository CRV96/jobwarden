package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.dto.OfferTotal;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.jobs.service.SalaryService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(JobOfferController.class)
class JobOfferControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private OfferService offers;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private SalaryService salaries;

	@BeforeEach
	void selectRobertWithAnOffer() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.OFFER)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.offers.find(1, 7)).willReturn(JobOffer.NONE);
		given(this.salaries.currencies()).willReturn(List.of("EUR", "RON", "USD"));
	}

	@Test
	void showsTheTermsToFillInWithTheBaseSalaryAsAFigure() {
		given(this.offers.find(1, 7)).willReturn(JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("8000.00"))
			.baseSalaryCurrency("RON")
			.baseSalaryPeriod(SalaryPeriod.MONTH)
			.timeOff("25 days")
			.build());

		assertThat(this.mvc.get().uri("/applications/7/offer").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The offer", "name=\"baseSalaryAmount\"", "value=\"8000\"", "value=\"25 days\"", "Compare offers",
					"hx-put=\"/applications/7/offer\"")
			.containsPattern("value=\"RON\"[^>]*selected")
			.containsPattern("value=\"MONTH\"[^>]*selected")
			.doesNotContain("Saved before as");
	}

	@Test
	void showsTheExtrasARowEachAndWhatTheOfferPaysAllIn() {
		JobOffer offer = JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("60000"))
			.baseSalaryCurrency("EUR")
			.extras(List.of(new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, BigDecimal.TEN, "EUR", PayFrequency.WORKING_DAY, null),
					new OfferExtra(OfferExtraKind.BONUS, BigDecimal.TEN, "%", PayFrequency.YEAR, "On targets")))
			.build();
		given(this.offers.find(1, 7)).willReturn(offer);
		given(this.offers.total(offer)).willReturn(Optional.of(new OfferTotal("EUR", new BigDecimal("60000"),
				new BigDecimal("8200"), new BigDecimal("68200"), new BigDecimal("5000"), BigDecimal.ZERO, 1)));

		assertThat(this.mvc.get().uri("/applications/7/offer").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("On top of the salary", "name=\"extras[0].kind\"", "name=\"extras[1].note\"", "value=\"On targets\"",
					"Meal vouchers", "per working day", "% of base", "data-remove-row",
					// A blank row to add, numbered by app.js
					"name=\"extras[i].amount\"", "data-add-row=\"offer-extras-7\"",
					"id=\"offer-total-7\"", "All in, a year", "€68,200", "€60,000 base salary + €8,200 on top",
					"And €5,000 once", "1 extra isn’t counted")
			.containsPattern("value=\"MEAL_VOUCHERS\"[^>]*selected")
			.containsPattern("value=\"WORKING_DAY\"[^>]*selected")
			.containsPattern("value=\"%\"[^>]*selected")
			.doesNotContain("each event");
	}

	@Test
	void startsTheFigureFromTheWordsSavedBefore() {
		given(this.offers.find(1, 7)).willReturn(JobOffer.builder().baseSalary("70000 eur per year").build());
		given(this.salaries.read("70000 eur per year")).willReturn(Optional.of(new SalaryRange(new BigDecimal("70000"),
				new BigDecimal("70000"), "EUR", SalaryPeriod.YEAR, null)));
		// All in, from the figure the card starts from
		JobOffer shown = JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("70000"))
			.baseSalaryCurrency("EUR")
			.baseSalaryPeriod(SalaryPeriod.YEAR)
			.build();
		given(this.offers.total(shown)).willReturn(Optional.of(new OfferTotal("EUR", new BigDecimal("70000"),
				BigDecimal.ZERO, new BigDecimal("70000"), BigDecimal.ZERO, BigDecimal.ZERO, 0)));

		assertThat(this.mvc.get().uri("/applications/7/offer").header("HX-Request", "true")).bodyText()
			.contains("value=\"70000\"", "Saved before as: 70000 eur per year.", "All in, a year", "€70,000")
			.containsPattern("value=\"YEAR\"[^>]*selected");
	}

	@Test
	void savesTheTermsAndReloadsWhatListsThemAndTheTotal() {
		JobOffer saved = JobOffer.builder().baseSalaryAmount(new BigDecimal("90000")).baseSalaryCurrency("EUR").build();
		given(this.offers.save(anyLong(), anyLong(), any())).willReturn(saved);
		given(this.offers.total(saved)).willReturn(Optional.of(new OfferTotal("EUR", new BigDecimal("90000"),
				new BigDecimal("8800"), new BigDecimal("98800"), BigDecimal.ZERO, BigDecimal.ZERO, 0)));

		assertThat(this.mvc.put()
			.uri("/applications/7/offer")
			.param("baseSalaryAmount", "90000")
			.param("baseSalaryCurrency", "EUR")
			.param("baseSalaryPeriod", "YEAR")
			.param("extras[0].kind", "MEAL_VOUCHERS")
			.param("extras[0].amount", "40")
			.param("extras[0].unit", "RON")
			.param("extras[0].frequency", "WORKING_DAY")
			.param("extras[0].note", "")
			.param("extras[1].kind", "EQUITY")
			.param("extras[1].amount", "")
			.param("extras[1].unit", "EUR")
			.param("extras[1].frequency", "YEAR")
			.param("extras[1].note", "0.05%, over 4 years")
			.param("respondBy", "2026-11-20")
			.header("HX-Request", "true")).hasStatusOk()
			.hasHeader("HX-Trigger", "applications-changed")
			.bodyText()
			// The total changes in its place, out of band
			.contains("Saved", "id=\"offer-total-7\"", "hx-swap-oob=\"true\"", "€98,800");
		then(this.offers).should().save(1, 7, JobOffer.builder()
			.baseSalaryAmount(new BigDecimal("90000"))
			.baseSalaryCurrency("EUR")
			.baseSalaryPeriod(SalaryPeriod.YEAR)
			.extras(List.of(
					new OfferExtra(OfferExtraKind.MEAL_VOUCHERS, new BigDecimal("40"), "RON", PayFrequency.WORKING_DAY, null),
					new OfferExtra(OfferExtraKind.EQUITY, null, "EUR", PayFrequency.YEAR, "0.05%, over 4 years")))
			.respondBy(LocalDate.of(2026, 11, 20))
			.build());
	}

	@Test
	void refusesABaseSalaryThatIsntAFigureAboveZero() {
		assertThat(this.mvc.put()
			.uri("/applications/7/offer")
			.param("baseSalaryAmount", "-5")
			.param("baseSalaryCurrency", "EUR")
			.header("HX-Request", "true")).bodyText()
			.contains("Type the amounts as numbers above zero");
		then(this.offers).should(never()).save(anyLong(), anyLong(), any());
	}

	@Test
	void refusesTermsTooLong() {
		assertThat(this.mvc.put()
			.uri("/applications/7/offer")
			.param("extras[0].kind", "BONUS")
			.param("extras[0].frequency", "YEAR")
			.param("extras[0].note", "x".repeat(201))
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("at most 200 characters");
		then(this.offers).should(never()).save(anyLong(), anyLong(), any());
	}

	@Test
	void asksClaudeForANegotiationBriefAndShowsIt() {
		AiRequest waiting = AiRequest.builder()
			.id(41)
			.kind(AiRequestKind.NEGOTIATE_OFFER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.findLatestRequest(1, AiRequestKind.NEGOTIATE_OFFER, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/negotiation/request").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Do JobWarden request 41", "every 5s", "/applications/7/negotiation/cancel");
		then(this.discovery).should().createRequest(1, AiRequestKind.NEGOTIATE_OFFER, 7);

		given(this.discovery.findLatestRequest(1, AiRequestKind.NEGOTIATE_OFFER, 7)).willReturn(Optional.empty());
		given(this.offers.findBrief(1, 7)).willReturn(Optional.of(NegotiationBrief.builder()
			.position("8% under the median in Germany.")
			.target("95,000 EUR, the median for a senior.")
			.walkAway("90,000 EUR")
			.leverage(List.of("Median 92,000 EUR (Stack Overflow 2025)"))
			.asks(List.of("A signing bonus of 5,000 EUR"))
			.script("Thank you, I’m excited…")
			.email("Dear Ana, …")
			.watchOut("They want an answer by Friday.")
			.sources(List.of("Stack Overflow survey 2025", "https://www.levels.fyi/companies/acme", "javascript:alert(1)"))
			.updatedAt(Instant.now())
			.build()));
		assertThat(this.mvc.get().uri("/applications/7/negotiation").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("8% under the median in Germany.", "95,000 EUR, the median for a senior.", "A signing bonus of 5,000 EUR",
					"Thank you, I’m excited…", "Dear Ana, …", "They want an answer by Friday.",
					"href=\"https://www.levels.fyi/companies/acme\"", "Write it again")
			.doesNotContain("every 5s", "href=\"javascript:");
	}

}
