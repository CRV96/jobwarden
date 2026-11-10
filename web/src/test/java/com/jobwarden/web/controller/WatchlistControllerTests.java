package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.watchlist.dto.CompanySuggestion;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final WatchedCompany ACME = WatchedCompany.builder()
		.id(7)
		.name("Acme")
		.ats(Ats.LEVER)
		.board("acme")
		.boardUrl("https://jobs.lever.co/acme")
		.addedAt(Instant.now())
		.lastCheckedAt(Instant.now())
		.newThisWeek(3)
		.build();

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
	private WatchlistService watchlist;

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.discovery.findSearches(1)).willReturn(List.of(new SavedSearch(3, "Remote Java",
				SearchCriteria.builder().titles(List.of("Java Developer")).build(), Instant.now())));
		given(this.watchlist.findCompanies(1)).willReturn(List.of(ACME));
		given(this.watchlist.suggest(eq(1L), anyInt())).willReturn(List.of(new CompanySuggestion("Initech", Ats.GREENHOUSE,
				"initech", "https://job-boards.greenhouse.io/initech", 2)));
	}

	@Test
	void showsTheCompaniesWatchedAndThoseTheJobsSuggest() {
		assertThat(this.mvc.get().uri("/discover/watchlist").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Watchlist", "with no Claude tokens", "href=\"https://jobs.lever.co/acme\"", "Acme", "Lever",
					"Last checked: Today", "3 new this week", "Stop watching Acme", "Check now",
					"Your jobs are on these boards too", "value=\"https://job-boards.greenhouse.io/initech\"",
					"Watch Initech: its Greenhouse board has 2 of your jobs")
			.doesNotContain("Save a search first");
	}

	@Test
	void asksForASavedSearchFirstWithoutOne() {
		given(this.discovery.findSearches(1)).willReturn(List.of());
		given(this.watchlist.findCompanies(1)).willReturn(List.of());

		assertThat(this.mvc.get().uri("/discover/watchlist")).hasStatusOk()
			.bodyText()
			.contains("Save a search first", "No companies yet")
			.doesNotContain("Check now");
	}

	@Test
	void watchesACompanyAndLoadsTheJobsToReviewAgain() {
		given(this.watchlist.watch(1, "https://jobs.lever.co/acme/1a2b", null)).willReturn(new WatchResult(ACME, true, 3));

		assertThat(this.mvc.post().uri("/discover/watchlist").param("link", " https://jobs.lever.co/acme/1a2b ")
			.header("HX-Request", "true")).hasStatusOk()
			.hasHeader("HX-Trigger", "found-jobs-changed")
			.bodyText()
			.contains("Watching Acme. 3 jobs match your saved searches.");
	}

	@Test
	void watchesASuggestionUnderItsName() {
		given(this.watchlist.watch(1, "https://job-boards.greenhouse.io/initech", "Initech"))
			.willReturn(new WatchResult(ACME, true, 0));

		assertThat(this.mvc.post().uri("/discover/watchlist").param("link", "https://job-boards.greenhouse.io/initech")
			.param("name", "Initech")).hasStatusOk()
			.doesNotContainHeader("HX-Trigger")
			.bodyText()
			.contains("No jobs match your saved searches yet");
	}

	@Test
	void saysWhyALinkCantBeWatchedInTheProfilesLanguageAndKeepsIt() {
		given(this.watchlist.watch(eq(1L), any(), any())).willThrow(new BusinessRuleException(
				WatchlistErrorCode.BOARD_NOT_FOUND, "No board", "https://jobs.lever.co/nobody"));

		assertThat(this.mvc.post().uri("/discover/watchlist").param("link", "https://jobs.lever.co/nobody")
			.header("Accept-Language", "de")).hasStatusOk()
			.bodyText()
			.contains("Unter https://jobs.lever.co/nobody gibt es keine Stellenseite: Prüfe den Link.",
					"value=\"https://jobs.lever.co/nobody\"", "Beobachtungsliste");
	}

	@Test
	void checksTheBoardsNowAndSaysWhatItFound() {
		given(this.watchlist.check(1)).willReturn(new WatchlistCheck(4, 1, 2));

		assertThat(this.mvc.post().uri("/discover/watchlist/check")).hasStatusOk()
			.hasHeader("HX-Trigger", "found-jobs-changed")
			.bodyText()
			.contains("Companies checked: 4. New jobs: 2. One board couldn’t be read.");
	}

	@Test
	void stopsWatchingACompany() {
		assertThat(this.mvc.post().uri("/discover/watchlist/7/delete")).hasStatusOk();

		then(this.watchlist).should().unwatch(1, 7);
	}

}
