package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import com.jobwarden.web.view.CvPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * With the real PDF renderer, like the CV page's tests.
 */
@WebMvcTest(TailoredCvController.class)
@Import(CvPdf.class)
class TailoredCvControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final TailoredCv TAILORED = TailoredCv.builder()
		.applicationId(7)
		.details(CvDetails.builder()
			.fullName("Robert Ciapă")
			.headline("Backend Engineer")
			.skills(List.of("Spring Boot", "Kafka"))
			.experience(List.of(new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Built payments")))
			.build())
		.matchedKeywords(List.of("Spring Boot", "Kafka"))
		.missingKeywords(List.of("Kubernetes"))
		.notes("Put Kafka first.")
		.updatedAt(Instant.now())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company("Globex")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.cvs.find(1)).willReturn(new Cv(null, TAILORED.details()));
	}

	@Test
	void offersToAskClaudeWhenThereIsNoneYet() {
		assertThat(this.mvc.get().uri("/applications/7/cv/card")).hasStatusOk()
			.bodyText()
			.contains("AI-proof CV", "Claude rewrites your CV for this job", "hx-post=\"/applications/7/cv/request\"",
					"Ask Claude to tailor it")
			.doesNotContain("every 5s");
	}

	@Test
	void sendsToTheCvPageWhenThereIsNoCv() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));

		assertThat(this.mvc.get().uri("/applications/7/cv/card")).bodyText()
			.contains("Claude needs your CV first", "href=\"/cv\"")
			.doesNotContain("/applications/7/cv/request");
	}

	@Test
	void asksClaudeAndShowsThePromptUntilClaudeIsDone() {
		AiRequest waiting = request(AiRequestStatus.WAITING);
		given(this.discovery.createRequest(1, AiRequestKind.TAILOR_CV, 7)).willReturn(waiting);
		given(this.discovery.findLatestRequest(1, AiRequestKind.TAILOR_CV, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/cv/request")).hasStatusOk()
			.bodyText()
			.contains("Waiting for Claude", "Do JobWarden request 14: call get_request with request_id 14 and profile_id 1",
					"hx-trigger=\"every 5s\"", "/applications/7/cv/cancel")
			.doesNotContain("Ask Claude to tailor it");
		then(this.discovery).should().createRequest(1, AiRequestKind.TAILOR_CV, 7);
	}

	@Test
	void showsTheCvClaudeTailoredWithHowItMatches() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TAILORED));
		given(this.discovery.findLatestRequest(1, AiRequestKind.TAILOR_CV, 7))
			.willReturn(Optional.of(request(AiRequestStatus.DONE)));

		assertThat(this.mvc.get().uri("/applications/7/cv/card")).bodyText()
			.contains("Tailored: Today", ">Spring Boot<", ">Kubernetes<", "Put Kafka first.", "/applications/7/cv/pdf",
					"Tailor it again")
			.doesNotContain("every 5s");
	}

	@Test
	void makesAPdfNamedForThePersonAndTheCompany() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TAILORED));

		assertThat(this.mvc.get().uri("/applications/7/cv/pdf")).hasStatusOk()
			.hasContentType("application/pdf")
			.satisfies(result -> assertThat(result.getResponse().getContentAsByteArray())
				.startsWith("%PDF".getBytes(StandardCharsets.US_ASCII)))
			.headers()
			.hasValue("Content-Disposition",
					"attachment; filename=\"CV Robert Ciapa - Globex.pdf\"; filename*=UTF-8''CV%20Robert%20Ciap%C4%83%20-%20Globex.pdf");
		assertThat(this.mvc.get().uri("/applications/7/cv")).hasViewName("cv/document")
			.bodyText()
			.contains("<h1>Robert Ciapă</h1>", "Backend Engineer", "Built payments");
	}

	@Test
	void answersNotFoundForAJobWithoutOne() {
		assertThat(this.mvc.get().uri("/applications/7/cv/pdf")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/applications/8/cv/card")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deletesIt() {
		assertThat(this.mvc.post().uri("/applications/7/cv/delete")).hasStatusOk();

		then(this.cvs).should().deleteTailored(1, 7);
	}

	private static AiRequest request(AiRequestStatus status) {
		return AiRequest.builder()
			.id(14)
			.kind(AiRequestKind.TAILOR_CV)
			.requirements(AiRequirements.NONE)
			.status(status)
			.applicationId(7L)
			.createdAt(Instant.now())
			.build();
	}

}
