package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.BaseLetter;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.exception.CvErrorCode;
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
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(LettersController.class)
class LettersControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CoverLetterService letters;

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

	/**
	 * Robert's jobs: one saved with a letter, one saved without, one applied to with messages to two people, and one
	 * rejected, which has no letter.
	 */
	@BeforeEach
	void selectRobertWithJobs() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.fullName("Robert Ciapă")
			.headline("Backend engineer")
			.experience(List.of())
			.build()));
		given(this.applications.find(eq(1L), any(ApplicationQuery.class))).willReturn(new PageImpl<>(List.of(
				summary(7, "Backend engineer", "Acme", ApplicationStatus.SAVED),
				summary(8, "Java developer", "Globex", ApplicationStatus.SAVED),
				summary(9, "Platform engineer", "Initech", ApplicationStatus.APPLIED),
				summary(10, "Rejected role", "Umbrella", ApplicationStatus.REJECTED))));
		for (long id = 7; id <= 10; id++) {
			given(this.applications.findById(1, id)).willReturn(Optional.of(details(id)));
		}
		given(this.letters.findLetters(eq(1L), anyCollection())).willReturn(Map.of(7L,
				new CoverLetter(7, "Dear hiring team,", null, Instant.now())));
		given(this.letters.countDrafts(eq(1L), anyCollection())).willReturn(Map.of(9L, 2));
		given(this.letters.findJobsWithLetter(eq(1L), anyCollection())).willReturn(java.util.Set.of(7L));
	}

	@Test
	void listsTheJobsWithTheirLettersAndLeavesOutThoseThatEndedWithoutOne() {
		assertThat(this.mvc.get().uri("/letters")).hasStatusOk()
			.bodyText()
			.contains("Cover letters", "Your base letter", "Backend engineer", "Java developer", "Platform engineer",
					"Written", "No letter yet", "Messages to 2 people", "Pick a job")
			.doesNotContain("Rejected role")
			// Only the jobs without a letter can be ticked
			.contains("name=\"ids\" value=\"8\"", "name=\"ids\" value=\"9\"")
			.doesNotContain("name=\"ids\" value=\"7\"");

		assertThat(this.mvc.get().uri("/letters").param("show", "WITHOUT")).hasStatusOk()
			.bodyText()
			.contains("Java developer", "Platform engineer")
			.doesNotContain("Acme");
	}

	@Test
	void opensThePickedJobsLetterAndMessagesBesideTheList() {
		assertThat(this.mvc.get().uri("/letters").param("job", "8")).hasStatusOk()
			.bodyText()
			.contains("id=\"letters-job\"", "/applications/8/letter/card", "/applications/8/outreach", "Open the job");

		assertThat(this.mvc.get().uri("/letters/jobs/9").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Platform engineer", "/applications/9/letter/card", "/applications/9/outreach");
	}

	@Test
	void asksClaudeForTheTickedJobsWithoutALetterInOnePrompt() {
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(), List.of(request(41, 8), request(42, 9)));

		assertThat(this.mvc.post()
			.uri("/letters/request")
			.param("ids", "7", "8", "9", "99")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The assistant has 2 letters to write", "Do JobWarden requests 41, 42, one after the other",
					"profile_id 1", "id=\"letter-row-8\"", "hx-swap-oob=\"true\"", "The AI Assistant is writing")
			// The rows on their own, where HTMX swaps them in place
			.doesNotContain("<ul");

		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_COVER_LETTER, 8);
		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_COVER_LETTER, 9);
		// It has a letter; it isn't Robert's
		then(this.discovery).should(never()).createRequest(1, AiRequestKind.WRITE_COVER_LETTER, 7);
		then(this.discovery).should(never()).createRequest(1, AiRequestKind.WRITE_COVER_LETTER, 99);
	}

	@Test
	void asksNothingOfClaudeWithoutACvOrForALetterItsWriting() {
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(request(41, 8)));
		this.mvc.post().uri("/letters/request").param("ids", "8").header("HX-Request", "true").exchange();
		then(this.discovery).should(never()).createRequest(anyLong(), any(), anyLong());

		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder().experience(List.of()).build()));
		this.mvc.post().uri("/letters/request").param("ids", "9").header("HX-Request", "true").exchange();
		then(this.discovery).should(never()).createRequest(anyLong(), any(), anyLong());
	}

	@Test
	void takesBackEveryLetterClaudeHasStillToWrite() {
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(request(41, 8), request(42, 9)), List.of());

		assertThat(this.mvc.post().uri("/letters/cancel").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("id=\"letters-waiting\"", "id=\"letter-row-9\"");

		then(this.discovery).should().cancelRequest(1, 41);
		then(this.discovery).should().cancelRequest(1, 42);
	}

	@Test
	void showsOneRowAgainWhileClaudeWritesItsLetter() {
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(request(41, 8)));

		assertThat(this.mvc.get().uri("/letters/rows/8").param("selected", "8").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("id=\"letter-row-8\"", "The AI Assistant is writing", "every 5s", "is-open");
	}

	@Test
	void keepsTheBaseLetterOrSaysWhyNot() {
		given(this.letters.findBaseLetter(1)).willReturn(Optional.of(new BaseLetter("Dear hiring team of {company},", Instant.now())));

		assertThat(this.mvc.get().uri("/letters/base").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Dear hiring team of {company},", "Change it");

		this.mvc.post().uri("/letters/base").param("body", "Dear Ana,").header("HX-Request", "true").exchange();
		then(this.letters).should().saveBaseLetter(1, "Dear Ana,");

		given(this.letters.saveBaseLetter(1, " ")).willThrow(
				new BusinessRuleException(CvErrorCode.COVER_LETTER_EMPTY, "The cover letter is empty"));
		assertThat(this.mvc.post().uri("/letters/base").param("body", " ").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The letter is empty.", "<textarea");

		this.mvc.post().uri("/letters/base/delete").header("HX-Request", "true").exchange();
		then(this.letters).should().deleteBaseLetter(1);
	}

	private static ApplicationSummary summary(long id, String title, String company, ApplicationStatus status) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.company(company)
			.status(status)
			.capturedAt(Instant.now())
			.build();
	}

	private static ApplicationDetails details(long id) {
		return ApplicationDetails.builder()
			.id(id)
			.title(switch ((int) id) {
				case 7 -> "Backend engineer";
				case 8 -> "Java developer";
				case 9 -> "Platform engineer";
				default -> "Rejected role";
			})
			.company("Acme")
			.status((id == 9) ? ApplicationStatus.APPLIED : ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build();
	}

	private static AiRequest request(long id, long jobId) {
		return AiRequest.builder()
			.id(id)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.applicationId(jobId)
			.build();
	}

}
