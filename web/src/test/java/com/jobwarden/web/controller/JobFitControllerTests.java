package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.FitFlag;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.enums.FitFlagKind;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
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

@WebMvcTest(JobFitController.class)
class JobFitControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private JobFitService fits;

	@MockitoBean
	private AutoApplyService autoApply;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobertWhoNeedsSponsorship() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.autoApply.findAnswers(1)).willReturn(ApplicationAnswers.builder().needsSponsorship(true).build());
	}

	@Test
	void showsTheScoreTheRequirementsAndWhatJobWardenNoticed() {
		given(this.fits.find(1, 7)).willReturn(Optional.of(new JobFit(new BigDecimal("4.4"), "Strong on Java; no Kubernetes.",
				List.of(new FitRequirement("5+ years of Java", RequirementImportance.MUST, RequirementMatch.MET, "8 years"),
						new FitRequirement("Kubernetes", RequirementImportance.NICE, RequirementMatch.MISSING, null)),
				Instant.now())));
		given(this.fits.findFlags(1, 7, true)).willReturn(List.of(
				new FitFlag(FitFlagKind.NO_SPONSORSHIP, "We do not sponsor visas.", null, null),
				new FitFlag(FitFlagKind.OPEN_LONG, null, LocalDate.of(2026, 8, 1), null),
				new FitFlag(FitFlagKind.REPOSTED, null, null, 5L)));

		assertThat(this.mvc.get().uri("/applications/7/fit").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains(">4.4<", "Strong fit", "st-offer", "Strong on Java; no Kubernetes.", "5+ years of Java", "8 years",
					"ph-check-circle", "ph-x-circle", "Rules out sponsoring a visa, which you need", "“We do not sponsor visas.”",
					"Up since 1 Aug 2026.", "/applications/5", "Score it again", "data-collapse=\"fit\"");
	}

	@Test
	void handsTheRequestToClaudeCode() {
		AiRequest waiting = AiRequest.builder()
			.id(40)
			.kind(AiRequestKind.SCORE_JOB)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.createRequest(1, AiRequestKind.SCORE_JOB, 7)).willReturn(waiting);
		given(this.discovery.findLatestRequest(1, AiRequestKind.SCORE_JOB, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/fit/request").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Do JobWarden request 40", "every 5s")
			.doesNotContain("Rules out");
		then(this.discovery).should().createRequest(1, AiRequestKind.SCORE_JOB, 7);
	}

}
