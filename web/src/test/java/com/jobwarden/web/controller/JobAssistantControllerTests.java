package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
import com.jobwarden.jobs.service.JobFitService;
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

@WebMvcTest(JobAssistantController.class)
class JobAssistantControllerTests {

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
	private JobFitService fits;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private CoverLetterService letters;

	@MockitoBean
	private InterviewBriefService briefs;

	@MockitoBean
	private OfferService offers;

	@BeforeEach
	void aJob() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.discovery.findLatestRequest(anyLong(), any(AiRequestKind.class), anyLong())).willReturn(Optional.empty());
		given(this.letters.findDrafts(1, 7)).willReturn(List.of());
	}

	@Test
	void showsTheFitAndEachCardsStateWithLinksToThem() {
		job(ApplicationStatus.APPLIED);
		given(this.fits.find(1, 7)).willReturn(Optional.of(new JobFit(new BigDecimal("4.4"),
				"Strong on Java and Kafka; no Kubernetes.", List.of(), Instant.now())));
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(new TailoredCv(7, CvDetails.NONE, List.of(), List.of(), null,
				Instant.now())));
		given(this.discovery.findLatestRequest(1, AiRequestKind.WRITE_COVER_LETTER, 7)).willReturn(Optional.of(AiRequest.builder()
			.id(30)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WORKING)
			.createdAt(Instant.now())
			.build()));

		assertThat(this.mvc.get().uri("/applications/7/assistant").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("From the AI Assistant", "4.4", "out of 5", "Strong fit", "Strong on Java and Kafka; no Kubernetes.",
					"href=\"#tailored-cv\"", "data-jump=\"tailored-cv\"", "AI-proof CV", "Generated", "Cover letter",
					"In progress", "Reach out", "Not generated", "Interview prep",
					// While the assistant writes the letter, it asks again
					"hx-trigger=\"every 5s\"")
			// The negotiation's card is there at Offer only
			.doesNotContain("Negotiation");
	}

	@Test
	void showsTheNegotiationAtOfferAndAsksNothingWhenTheAssistantIsOnNone() {
		job(ApplicationStatus.OFFER);

		assertThat(this.mvc.get().uri("/applications/7/assistant").header("HX-Request", "true")).bodyText()
			.contains("Negotiation", "href=\"#negotiation\"")
			.doesNotContain("every 5s", "out of 5");
	}

	private void job(ApplicationStatus status) {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(status)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
	}

}
