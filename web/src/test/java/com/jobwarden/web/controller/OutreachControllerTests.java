package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
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

@WebMvcTest(OutreachController.class)
class OutreachControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final OutreachDraft DRAFT = OutreachDraft.builder()
		.id(5)
		.applicationId(7)
		.recipientName("Ana Pop")
		.recipientRole("Technical Recruiter at Acme")
		.recipientLink("https://www.linkedin.com/in/anapop")
		.recipientEmail("ana@acme.example")
		.linkedinNote("Hi Ana, I saw the Java role at Acme.")
		.emailSubject("The Java Developer role")
		.emailBody("Dear Ana, I build payment systems.")
		.updatedAt(Instant.now())
		.build();

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

	@BeforeEach
	void selectRobertWithAJob() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder().fullName("Robert Ciapă").build()));
	}

	@Test
	void offersToAskClaudeAndSaysItNeverSendsAnything() {
		assertThat(this.mvc.get().uri("/applications/7/outreach").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Reach out", "300 characters at most", "/applications/7/outreach/request");

		this.mvc.post().uri("/applications/7/outreach/request").header("HX-Request", "true").exchange();
		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_OUTREACH, 7);
	}

	@Test
	void showsTheMessagesToCopyAndSendThemselves() {
		given(this.letters.findDrafts(1, 7)).willReturn(List.of(DRAFT));

		assertThat(this.mvc.get().uri("/applications/7/outreach").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Ana Pop", "Technical Recruiter at Acme", "Hi Ana, I saw the Java role at Acme.", "36/300",
					"href=\"https://www.linkedin.com/in/anapop\"", "data-copy-from=\"subject-5 body-5\"",
					"mailto:ana@acme.example?subject=The%20Java%20Developer%20role", "never sends anything");
	}

	@Test
	void keepsTheChangesOrSaysTheNoteIsTooLong() {
		given(this.letters.findDrafts(1, 7)).willReturn(List.of(DRAFT));
		given(this.letters.changeDraft(1, 5, "Hi Ana!", "Subject", "Body")).willReturn(DRAFT.toBuilder().linkedinNote("Hi Ana!").build());
		assertThat(this.mvc.post()
			.uri("/applications/7/outreach/5")
			.param("linkedinNote", "Hi Ana!")
			.param("emailSubject", "Subject")
			.param("emailBody", "Body")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Hi Ana!", "7/300");

		String tooLong = "x".repeat(301);
		given(this.letters.changeDraft(1, 5, tooLong, null, null)).willThrow(new BusinessRuleException(
				CvErrorCode.LINKEDIN_NOTE_TOO_LONG, "Too long", "300", "301"));
		assertThat(this.mvc.post().uri("/applications/7/outreach/5").param("linkedinNote", tooLong).header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.contains("A LinkedIn note can have at most 300 characters; this one has 301.", tooLong);
	}

}
