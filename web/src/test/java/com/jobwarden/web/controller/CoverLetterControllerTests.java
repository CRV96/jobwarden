package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.BaseLetter;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
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
import com.jobwarden.web.view.CvPdf;
import com.jobwarden.web.view.LetterPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(CoverLetterController.class)
@Import({ CvPdf.class, LetterPdf.class })
class CoverLetterControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final CoverLetter LETTER = new CoverLetter(7,
			"Dear hiring team,\n\nI build payment systems at Globex.\n\nKind regards,", "Led with Kafka.", Instant.now());

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
			.title("Backend engineer")
			.company("Acme")
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.fullName("Robert Ciapă")
			.email("robert@example.com")
			.experience(List.of())
			.build()));
	}

	@Test
	void offersToAskClaudeOrToWriteItOneself() {
		assertThat(this.mvc.get().uri("/applications/7/letter/card").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Cover letter", "Ask Claude to write it", "/applications/7/letter/request", "Write it myself");
	}

	@Test
	void startsALetterWrittenWithoutClaudeFromTheBaseLetter() {
		given(this.letters.findBaseLetter(1)).willReturn(Optional.of(
				new BaseLetter("Dear hiring team,\n\nI'd like to join {company} as {role}.", Instant.now())));

		assertThat(this.mvc.get().uri("/applications/7/letter/edit").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("I&#39;d like to join Acme as Backend engineer.", "Started from your base letter");

		// A letter it has stays as it is
		given(this.letters.findLetter(1, 7)).willReturn(Optional.of(LETTER));
		assertThat(this.mvc.get().uri("/applications/7/letter/edit").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("I build payment systems at Globex.")
			.doesNotContain("join Acme", "Started from your base letter");
	}

	@Test
	void handsTheRequestToClaudeCode() {
		// The Cover letters page's row of the job loads again
		assertThat(this.mvc.post().uri("/applications/7/letter/request").header("HX-Request", "true")).headers()
			.hasValue("HX-Trigger", "letters-changed");

		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_COVER_LETTER, 7);
	}

	@Test
	void showsTheLetterWithItsPdf() {
		given(this.letters.findLetter(1, 7)).willReturn(Optional.of(LETTER));

		assertThat(this.mvc.get().uri("/applications/7/letter/card").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("I build payment systems at Globex.", "Led with Kafka.", "/applications/7/letter/pdf", "Change it");
		assertThat(this.mvc.get().uri("/applications/7/letter/pdf")).hasStatusOk()
			.hasContentType("application/pdf")
			.satisfies(result -> assertThat(result.getResponse().getContentAsByteArray())
				.startsWith("%PDF".getBytes(StandardCharsets.US_ASCII)));
		assertThat(this.mvc.get().uri("/applications/7/letter")).hasStatusOk()
			.bodyText()
			.contains("Robert Ciapă", "robert@example.com", "Acme", "Re: Backend engineer", "I build payment systems at Globex.");
	}

	@Test
	void keepsTheLetterAsChangedOrSaysWhyNot() {
		given(this.letters.changeLetter(1, 7, "Dear Ana,")).willReturn(LETTER);
		assertThat(this.mvc.post().uri("/applications/7/letter").param("body", "Dear Ana,").header("HX-Request", "true"))
			.hasStatusOk();
		then(this.letters).should().changeLetter(1, 7, "Dear Ana,");

		given(this.letters.changeLetter(1, 7, " ")).willThrow(
				new BusinessRuleException(CvErrorCode.COVER_LETTER_EMPTY, "The cover letter is empty"));
		assertThat(this.mvc.post().uri("/applications/7/letter").param("body", " ").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The letter is empty.", "<textarea");
	}

}
