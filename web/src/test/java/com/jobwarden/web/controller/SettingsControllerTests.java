package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * The language picker is covered by LocalizationTests.
 */
@WebMvcTest(SettingsController.class)
class SettingsControllerTests {

	private static final User ROBERT = new User(1000, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@Test
	void showsWhatTheExtensionNeeds() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("http://localhost", ">1000<", "data-theme-choice");
	}

	@Test
	void showsHowToConnectClaude() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains(">http://localhost/mcp<",
					"claude mcp add --transport http jobwarden http://localhost/mcp --header &quot;X-JobWarden-Profile: 1000&quot;");
	}

	@Test
	void opensWithoutAProfile() {
		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("Choose a profile first", "href=\"/profiles\"");
	}

}
