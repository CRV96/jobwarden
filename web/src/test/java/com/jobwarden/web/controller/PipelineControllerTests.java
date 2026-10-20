package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(PipelineController.class)
class PipelineControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@Test
	void putsEachApplicationInTheColumnOfItsStatus() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findAll(1, null)).willReturn(List.of(
				summary(7, "Backend engineer", ApplicationStatus.OFFER),
				summary(8, "Platform engineer", ApplicationStatus.SAVED)));

		assertThat(this.mvc.get().uri("/pipeline")).hasStatusOk()
			.hasViewName("pipeline/board")
			.bodyText()
			.contains("Backend engineer", "Platform engineer", "/applications/7/drawer", "st-withdrawn", "Acme · Remote")
			// The columns go in the order of the statuses, whatever order the applications come in
			.containsSubsequence("st-saved", "Platform engineer", "st-applied", "st-interviewing", "st-offer",
					"Backend engineer", "st-rejected");
	}

	@Test
	void sendsTheBrowserToTheProfilePickerWhenNoProfileIsSelected() {
		assertThat(this.mvc.get().uri("/pipeline")).hasRedirectedUrl("/profiles");
	}

	private static ApplicationSummary summary(long id, String title, ApplicationStatus status) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.company("Acme")
			.location("Remote")
			.status(status)
			.capturedAt(Instant.now())
			.build();
	}

}
