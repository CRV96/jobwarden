package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(SetupGuideController.class)
class SetupGuideControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private InboxService inbox;

	@MockitoBean
	private WatchlistService watchlist;

	@BeforeEach
	void robert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));
		given(this.discovery.findRequests(eq(1L), anyInt())).willReturn(List.of());
		given(this.watchlist.findCompanies(1)).willReturn(List.of());
	}

	@Test
	void saysWhatToSetUpForEachPartToWork() {
		assertThat(this.mvc.get().uri("/setup").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Set up JobWarden", "0 of 5 done", "data-complete=\"false\"", "Add your CV", "Connect your AI assistant",
					"Install the browser extension", "Connect your mailbox", "Watch the companies you’d work for",
					"Turns on: recruiters’ replies", "href=\"/cv\"", "href=\"/inbox\"", "href=\"/watchlist\"",
					"https://chromewebstore.google.com/detail/bjklifdmffkhiffbicofeohgdcpmobek",
					"https://addons.mozilla.org/firefox/addon/jobwarden/", "Don’t show this again", "Later")
			.doesNotContain("All set");
	}

	@Test
	void ticksWhatTheProfileHasDone() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder().fullName("Robert").build()));
		given(this.applications.findSourcesWithIcon(1)).willReturn(Set.of("linkedin.com"));
		given(this.inbox.findAccount(1)).willReturn(Optional.of(new MailAccount("imap.gmail.com", 993, "robert@gmail.com",
				"INBOX", null, null, null, null, 10, InboxAutomation.ASK)));

		assertThat(this.mvc.get().uri("/setup").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("3 of 5 done", "width: 60%", "href=\"/settings#assistant\"", "href=\"/watchlist\"")
			.doesNotContain("href=\"/cv\"", "href=\"/inbox\"", "chromewebstore.google.com");
	}

}
