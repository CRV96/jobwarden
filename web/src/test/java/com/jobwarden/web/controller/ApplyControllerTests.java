package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(ApplyController.class)
class ApplyControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final ApplicationSummary SAVED = ApplicationSummary.builder()
		.id(7)
		.title("Backend engineer")
		.company("Acme")
		.link("https://jobs.lever.co/acme/7")
		.source("jobs.lever.co")
		.status(ApplicationStatus.SAVED)
		.capturedAt(Instant.now())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AutoApplyService autoApply;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.cvs.find(1)).willReturn(new Cv(null,
				CvDetails.builder().fullName("Robert Ciapă").email("robert@example.com").build()));
		given(this.autoApply.findAnswers(1)).willReturn(ApplicationAnswers.builder()
			.workAuthorization("EU citizen")
			.needsSponsorship(false)
			.declineVoluntary(true)
			.custom(List.of(new CustomAnswer("Driving licence?", "Yes, B")))
			.build());
		given(this.applications.findAll(1, ApplicationStatus.SAVED)).willReturn(List.of(SAVED,
				ApplicationSummary.builder().id(8).title("Without a link").status(ApplicationStatus.SAVED)
					.capturedAt(Instant.now()).build()));
	}

	@Test
	void showsTheJobsToApplyToAndTheAnswers() {
		assertThat(this.mvc.get().uri("/apply")).hasStatusOk()
			.hasViewName("apply/apply")
			.bodyText()
			.contains("Auto-apply", "name=\"ids\" value=\"7\"", "Backend engineer", "Acme · jobs.lever.co",
					"value=\"SUBMIT\" checked", "Apply with Claude", "value=\"EU citizen\"", "Driving licence?",
					"name=\"custom[0].answer\"", "robert@example.com", "LinkedIn Easy Apply")
			.doesNotContain("Without a link", "Claude, applying");
	}

	@Test
	void savesTheAnswers() {
		assertThat(this.mvc.post()
			.uri("/apply/answers")
			.param("workAuthorization", "EU citizen")
			.param("needsSponsorship", "")
			.param("willingToRelocate", "true")
			.param("yearsOfExperience", "8")
			.param("coverLetter", "NONE")
			.param("declineVoluntary", "true")
			.param("custom[0].question", "Driving licence?")
			.param("custom[0].answer", "Yes, B")).hasRedirectedUrl("/apply?saved");

		ArgumentCaptor<ApplicationAnswers> saved = ArgumentCaptor.forClass(ApplicationAnswers.class);
		then(this.autoApply).should().saveAnswers(eq(1L), saved.capture());
		assertThat(saved.getValue()).satisfies(answers -> {
			assertThat(answers.needsSponsorship()).isNull();
			assertThat(answers.willingToRelocate()).isTrue();
			assertThat(answers.yearsOfExperience()).isEqualTo(8);
			assertThat(answers.coverLetter()).isEqualTo(CoverLetter.NONE);
			assertThat(answers.declineVoluntary()).isTrue();
			assertThat(answers.custom()).containsExactly(new CustomAnswer("Driving licence?", "Yes, B"));
		});
	}

	@Test
	void showsTheAnswersAgainWhenOneIsInvalid() {
		assertThat(this.mvc.post().uri("/apply/answers").param("yearsOfExperience", "99").param("noticePeriod", "1 month"))
			.hasStatusOk()
			.bodyText()
			.contains("Years of experience go from 0 to 60.", "value=\"1 month\"");
		then(this.autoApply).should(never()).saveAnswers(anyLong(), any());
	}

	@Test
	void queuesTheTickedJobsAndAsksClaude() {
		given(this.autoApply.queue(1, List.of(7L), ApplyMode.REVIEW)).willReturn(List.of(item(ApplyStatus.QUEUED)));

		assertThat(this.mvc.post().uri("/apply/queue").param("ids", "7").param("mode", "REVIEW")).hasRedirectedUrl("/apply");

		then(this.discovery).should().createRequest(eq(1L), eq(AiRequestKind.AUTO_APPLY), any(AiRequirements.class));
		assertThat(this.mvc.post().uri("/apply/queue").param("mode", "SUBMIT")).hasRedirectedUrl("/apply")
			.flash()
			.containsEntry("applyError", true);
	}

	@Test
	void showsThePromptAndHowEachJobWent() {
		given(this.discovery.findRequests(1, ApplyController.ITEMS_SHOWN)).willReturn(List.of(AiRequest.builder()
			.id(30)
			.kind(AiRequestKind.AUTO_APPLY)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build()));
		ApplyItem needsAnswer = ApplyItem.builder()
			.id(4)
			.applicationId(9)
			.title("Data engineer")
			.company("Globex")
			.mode(ApplyMode.SUBMIT)
			.status(ApplyStatus.NEEDS_ANSWER)
			.site("Workday")
			.question("Willing to work weekends?")
			.createdAt(Instant.now())
			.build();
		given(this.autoApply.findItems(1, ApplyController.ITEMS_SHOWN)).willReturn(List.of(item(ApplyStatus.FILLED), needsAnswer));

		assertThat(this.mvc.get().uri("/apply")).bodyText()
			.contains("Claude, applying", "Do JobWarden request 30", "claude --chrome", "hx-trigger=\"every 5s\"",
					"Filled in, for you to send", "/apply/items/3/sent", "Needs your answer", "Willing to work weekends?",
					"/apply/items/4/answer")
			// A job Claude has filled in isn't offered again
			.doesNotContain("name=\"ids\" value=\"7\"");
	}

	@Test
	void answersAQuestionAndAsksClaudeAgain() {
		assertThat(this.mvc.post().uri("/apply/items/4/answer").param("answer", "Now and then")).hasRedirectedUrl("/apply");

		then(this.autoApply).should().answer(1, 4, "Now and then");
		then(this.discovery).should().createRequest(eq(1L), eq(AiRequestKind.AUTO_APPLY), any(AiRequirements.class));
	}

	@Test
	void marksAFilledFormSent() {
		assertThat(this.mvc.post().uri("/apply/items/3/sent")).hasRedirectedUrl("/apply");

		then(this.autoApply).should().markSent(1, 3);
	}

	private static ApplyItem item(ApplyStatus status) {
		return ApplyItem.builder()
			.id(3)
			.applicationId(7)
			.title("Backend engineer")
			.company("Acme")
			.link(SAVED.link())
			.mode(ApplyMode.REVIEW)
			.status(status)
			.createdAt(Instant.now())
			.build();
	}

}
