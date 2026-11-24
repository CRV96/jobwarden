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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
		given(this.cvs.find(1)).willReturn(new Cv(null, TAILORED.details().toBuilder().email("robert@example.com").build()));
	}

	@Test
	void offersToAskClaudeWhenThereIsNoneYet() {
		assertThat(this.mvc.get().uri("/applications/7/cv/card")).hasStatusOk()
			.bodyText()
			.contains("AI-proof CV", "The assistant rewrites your CV for this job", "hx-post=\"/applications/7/cv/request\"",
					"Ask the AI Assistant to tailor it")
			.doesNotContain("every 5s");
	}

	/** A CV starts with whose it is: without a name and an email on the CV page, they're asked for first. */
	@Test
	void asksForTheNameAndEmailFirstWhenTheCvPageHasNone() {
		given(this.cvs.find(1)).willReturn(new Cv(null, TAILORED.details()));

		assertThat(this.mvc.get().uri("/applications/7/cv/card")).hasStatusOk()
			.bodyText()
			.contains("Add your name and contact details first", "hx-post=\"/applications/7/cv/contacts\"",
					"name=\"ask\" value=\"true\"", "value=\"Robert Ciapă\"", "name=\"email\"", "name=\"github\"",
					"Save and ask the AI Assistant", "Ask without them", "hx-post=\"/applications/7/cv/request\"")
			.doesNotContain("Ask the AI Assistant to tailor it");
	}

	@Test
	void savesTheNameAndContactDetailsThenAsksTheAssistant() {
		given(this.cvs.find(1)).willReturn(new Cv(null, TAILORED.details()));

		assertThat(this.mvc.post()
			.uri("/applications/7/cv/contacts")
			.param("fullName", "Robert Ciapă")
			.param("email", "robert@example.com")
			.param("phone", "+40 700 000 000")
			.param("linkedin", "https://www.linkedin.com/in/robert")
			.param("ask", "true")).hasStatusOk();

		then(this.cvs).should().saveContacts(eq(1L), argThat(contacts -> contacts != null
				&& "robert@example.com".equals(contacts.email()) && "+40 700 000 000".equals(contacts.phone())
				&& "https://www.linkedin.com/in/robert".equals(contacts.linkedin())));
		then(this.discovery).should().createRequest(1, AiRequestKind.TAILOR_CV, 7L);
	}

	@Test
	void showsTheContactDetailsAgainWithWhatsMissing() {
		given(this.cvs.find(1)).willReturn(new Cv(null, TAILORED.details()));

		assertThat(this.mvc.post().uri("/applications/7/cv/contacts").param("fullName", "Robert Ciapă").param("ask", "true"))
			.hasStatusOk()
			.bodyText()
			.contains("An email address is needed", "role=\"alert\"", "value=\"Robert Ciapă\"");
		then(this.cvs).should(never()).saveContacts(anyLong(), any());
		then(this.discovery).should(never()).createRequest(anyLong(), any(), any());
	}

	@Test
	void sendsToTheCvPageWhenThereIsNoCv() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));

		assertThat(this.mvc.get().uri("/applications/7/cv/card")).bodyText()
			.contains("The assistant needs your CV first", "href=\"/cv\"")
			.doesNotContain("/applications/7/cv/request");
	}

	@Test
	void asksClaudeAndShowsThePromptUntilClaudeIsDone() {
		AiRequest waiting = request(AiRequestStatus.WAITING);
		given(this.discovery.createRequest(1, AiRequestKind.TAILOR_CV, 7)).willReturn(waiting);
		given(this.discovery.findLatestRequest(1, AiRequestKind.TAILOR_CV, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/cv/request")).hasStatusOk()
			.bodyText()
			.contains("Waiting for the AI Assistant", "Do JobWarden request 14: call get_request with request_id 14 and profile_id 1",
					"hx-trigger=\"every 5s\"", "/applications/7/cv/cancel")
			.doesNotContain("Ask the AI Assistant to tailor it");
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
	void showsItsFieldsToChangeByHand() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TAILORED));

		assertThat(this.mvc.get().uri("/applications/7/cv/edit")).hasStatusOk()
			.hasViewName("applications/tailored-cv-edit")
			.bodyText()
			.contains("Edit the CV for this job", "Backend engineer · Globex", "Put Kafka first.", "Kubernetes",
					"value=\"Backend Engineer\"", "Spring Boot, Kafka", "Built payments",
					"action=\"/applications/7/cv/edit\"", "src=\"/applications/7/cv\"",
					"Your name and contact details come from your CV page", "href=\"/cv#contacts\"")
			.as("the CV page's name and contacts are the CV's")
			.doesNotContain("name=\"fullName\"", "name=\"email\"");
		assertThat(this.mvc.get().uri("/applications/8/cv/edit")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void savesItsFieldsKeepingWhatTheAssistantSaidOfIt() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TAILORED));

		assertThat(this.mvc.post()
			.uri("/applications/7/cv/edit")
			.param("headline", "Senior Backend Engineer")
			.param("skills", "Kafka, Spring Boot")
			.param("experience[0].role", "Java Developer")
			.param("experience[0].company", "Acme")
			.param("experience[0].description", "Built payments in Kafka")).hasRedirectedUrl("/applications/7/cv/edit?saved");

		then(this.cvs).should().saveTailored(eq(1L), eq(7L), argThat(details -> details != null
				&& "Robert Ciapă".equals(details.fullName()) && "Senior Backend Engineer".equals(details.headline())
				&& details.skills().size() == 2
				&& "Built payments in Kafka".equals(details.experience().getFirst().description())),
				eq(List.of("Spring Boot", "Kafka")), eq(List.of("Kubernetes")), eq("Put Kafka first."));
	}

	@Test
	void showsTheFieldsAgainWithWhatsWrong() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TAILORED));

		String tooLong = "Senior ".repeat(30);
		assertThat(this.mvc.post().uri("/applications/7/cv/edit").param("headline", tooLong))
			.hasStatusOk()
			.hasViewName("applications/tailored-cv-edit")
			.bodyText()
			.contains("value=\"" + tooLong + "\"", "role=\"alert\"", "at most 200 characters");
		then(this.cvs).should(never()).saveTailored(anyLong(), anyLong(), any(), any(), any(), any());
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
