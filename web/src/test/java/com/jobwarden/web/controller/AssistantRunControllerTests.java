package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.assistant.AssistantRunner;
import com.jobwarden.web.assistant.RunRefusedException;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@WebMvcTest(AssistantRunController.class)
class AssistantRunControllerTests {

	private static final User CODEX_USER = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"), null,
			Assistant.CODEX);

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

	@MockitoBean
	private AssistantRunner runner;

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(CODEX_USER));
		given(this.discovery.findRequest(1, 12)).willReturn(request(12, AiRequestKind.FIND_JOBS_FOR_CV, AiRequestStatus.WAITING));
	}

	@Test
	void startsTheProfilesAssistantOnTheRequestWithThisServersMcp() {
		assertThat(run("12", "http://localhost")).hasStatusOk()
			.bodyText()
			.contains("data-run", "Started: Codex works on it in the background.");
		then(this.runner).should().run(Assistant.CODEX, 1, List.of(12L), "http://localhost/mcp");
	}

	@Test
	void saysWhyItDidntStartAndKeepsThePrompt() {
		willThrow(new RunRefusedException(RunRefusedException.Reason.UNAVAILABLE)).given(this.runner)
			.run(any(), anyLong(), any(), anyString());

		assertThat(run("12", null)).hasStatusOk()
			.bodyText()
			.contains("Codex isn’t on this computer, or JobWarden can’t run it: paste the prompt instead.");
	}

	@Test
	void saysHowToSignTheAssistantInWhenItIsnt() {
		willThrow(new RunRefusedException(RunRefusedException.Reason.SIGNED_OUT)).given(this.runner)
			.run(any(), anyLong(), any(), anyString());

		assertThat(run("12", null)).hasStatusOk()
			.bodyText()
			.contains("Codex isn’t signed in on this computer: run codex login in a terminal once, then Run it here again.");
	}

	@Test
	void runsOnlyForThisAppsOwnPages() {
		// A form on another site can send neither HTMX's header nor this server's origin
		assertThat(this.mvc.post().uri("/assistant/run").param("requests", "12")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(run("12", "https://evil.example")).hasStatus(HttpStatus.FORBIDDEN);
		then(this.runner).should(never()).run(any(), anyLong(), any(), anyString());
	}

	@Test
	void runsOnlyRequestsWaitingAndNeverAutoApply() {
		given(this.discovery.findRequest(1, 13)).willReturn(request(13, AiRequestKind.SCORE_JOB, AiRequestStatus.WORKING));
		given(this.discovery.findRequest(1, 14)).willReturn(request(14, AiRequestKind.AUTO_APPLY, AiRequestStatus.WAITING));

		assertThat(run("13", "http://localhost")).hasStatus(HttpStatus.CONFLICT);
		assertThat(run("14", "http://localhost")).hasStatus(HttpStatus.CONFLICT);
		then(this.runner).should(never()).run(any(), anyLong(), any(), anyString());
	}

	private MvcTestResult run(String requests, String origin) {
		var post = this.mvc.post().uri("/assistant/run").param("requests", requests.split(",")).header("HX-Request", "true");
		if (origin != null) {
			post = post.header(HttpHeaders.ORIGIN, origin);
		}
		return post.exchange();
	}

	private static AiRequest request(long id, AiRequestKind kind, AiRequestStatus status) {
		return AiRequest.builder()
			.id(id)
			.kind(kind)
			.requirements(AiRequirements.NONE)
			.status(status)
			.createdAt(Instant.now())
			.build();
	}

}
