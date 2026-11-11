package com.jobwarden.web.controller;

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
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
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
	}

	@Test
	void showsTheTermsToFillIn() {
		given(this.offers.find(1, 7)).willReturn(JobOffer.builder().baseSalary("90,000 EUR").timeOff("25 days").build());

		assertThat(this.mvc.get().uri("/applications/7/offer").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The offer", "value=\"90,000 EUR\"", "value=\"25 days\"", "Compare offers", "hx-put=\"/applications/7/offer\"");
	}

	@Test
	void savesTheTermsAndReloadsWhatListsThem() {
		assertThat(this.mvc.put()
			.uri("/applications/7/offer")
			.param("baseSalary", " 90,000 EUR ")
			.param("bonus", "")
			.param("respondBy", "2026-11-20")
			.header("HX-Request", "true")).hasStatusOk()
			.hasHeader("HX-Trigger", "applications-changed")
			.bodyText()
			.contains("Saved");
		then(this.offers).should().save(1, 7, JobOffer.builder().baseSalary("90,000 EUR").respondBy(LocalDate.of(2026, 11, 20)).build());
	}

	@Test
	void refusesTermsTooLong() {
		assertThat(this.mvc.put()
			.uri("/applications/7/offer")
			.param("bonus", "x".repeat(201))
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
