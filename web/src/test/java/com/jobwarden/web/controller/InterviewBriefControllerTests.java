package com.jobwarden.web.controller;

import java.time.Instant;
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
import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(InterviewBriefController.class)
class InterviewBriefControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private InterviewBriefService briefs;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobertWithAJob() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company("Acme")
			.status(ApplicationStatus.INTERVIEWING)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
	}

	@Test
	void offersToAskClaudeForABrief() {
		assertThat(this.mvc.get().uri("/applications/7/prep").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Interview prep", "Claude finds out about the company", "/applications/7/prep/request",
					"Ask Claude to prepare me")
			.doesNotContain("every 5s");
	}

	@Test
	void handsTheRequestToClaudeCodeAndFollowsIt() {
		AiRequest waiting = AiRequest.builder()
			.id(31)
			.kind(AiRequestKind.PREPARE_INTERVIEW)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.createRequest(1, AiRequestKind.PREPARE_INTERVIEW, 7)).willReturn(waiting);
		given(this.discovery.findLatestRequest(1, AiRequestKind.PREPARE_INTERVIEW, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/prep/request").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Do JobWarden request 31: call get_request with request_id 31", "every 5s", "/applications/7/prep/cancel");
		then(this.discovery).should().createRequest(1, AiRequestKind.PREPARE_INTERVIEW, 7);
	}

	@Test
	void showsTheBrief() {
		given(this.briefs.find(1, 7)).willReturn(Optional.of(InterviewBrief.builder()
			.company("Acme makes payment terminals.")
			.role("The payments API team.")
			.questions(List.of("How would you make a payment idempotent?"))
			.stories(List.of("The Kafka migration at Globex."))
			.askThem(List.of("What does on-call look like?"))
			.watchOut("Layoffs in March.")
			.sources(List.of("https://acme.example.com/about", "javascript:alert(1)"))
			.updatedAt(Instant.now())
			.build()));

		assertThat(this.mvc.get().uri("/applications/7/prep").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Acme makes payment terminals.", "How would you make a payment idempotent?",
					"The Kafka migration at Globex.", "What does on-call look like?", "Layoffs in March.",
					"href=\"https://acme.example.com/about\"", "Prepare it again")
			// Only web addresses are links
			.doesNotContain("href=\"javascript:");
	}

}
