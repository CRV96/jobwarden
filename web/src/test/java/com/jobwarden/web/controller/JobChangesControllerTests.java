package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(JobChangesController.class)
class JobChangesControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private ApplicationService applications;

	// LayoutAdvice's, which every web test loads
	@MockitoBean
	private UserService users;

	@MockitoBean
	private DiscoveryService discovery;

	@Test
	void saysHowManyJobsThereAreAndWhenTheyLastChanged() {
		Instant changed = Instant.parse("2026-11-23T16:00:00Z");
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.count(1)).willReturn(58L);
		given(this.applications.find(eq(1L), argThat(query -> query != null && query.sort() == ApplicationSort.MODIFIED
				&& query.size() == 1)))
			.willReturn(new PageImpl<>(List.of(ApplicationSummary.builder()
				.id(9)
				.title("Backend engineer")
				.status(ApplicationStatus.SAVED)
				.capturedAt(changed)
				.build())));

		assertThat(this.mvc.get().uri("/applications/changes")).hasStatusOk()
			.hasHeader("Cache-Control", "no-store")
			.bodyJson()
			.isLenientlyEqualTo("""
					{"count": 58, "version": "58@%d", "subtitle": "58 postings saved from the extension or added by hand"}
					""".formatted(changed.toEpochMilli()));
	}

	@Test
	void saysNothingWithoutAProfile() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/applications/changes")).hasStatus(HttpStatus.NO_CONTENT);
	}

}
