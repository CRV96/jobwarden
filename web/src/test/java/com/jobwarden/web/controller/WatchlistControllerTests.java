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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final WatchedCompany ACME = company(7, "Acme", Ats.LEVER, "acme");

	private static final WatchedCompany GLOBEX = company(8, "Globex", Ats.GREENHOUSE, "globex");

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
		given(this.discovery.countToReview(1, null)).willReturn(12L);
		given(this.watchlist.findCompanies(1)).willReturn(List.of(ACME));
		given(this.watchlist.suggest(eq(1L), anyInt())).willReturn(List.of(new CompanySuggestion("Initech", Ats.GREENHOUSE,
				"initech", "https://job-boards.greenhouse.io/initech", 2)));
	}

	@Test
	void hasAPageOfItsOwnInTheSidebar() {
		assertThat(this.mvc.get().uri("/watchlist")).hasStatusOk()
			.bodyText()
			.containsPattern("href=\"/watchlist\"\\s+aria-current=\"page\"")
			.contains("<title>Watchlist · JobWarden</title>",
					"with no AI tokens", "Watch a company", "Companies you watch",
					"href=\"https://jobs.lever.co/acme\"", "Acme", "Lever", "Last checked: Today", "3 new this week",
					"Stop watching Acme", "Check now", "Your jobs are on these boards too",
					"value=\"https://job-boards.greenhouse.io/initech\"",
					"Watch Initech: its Greenhouse board has 2 of your jobs", "How it works",
					"click the eye at the top of JobWarden’s popup", "Jobs to review", ">12<")
			.doesNotContain("Save a search first");
	}

	@Test
	void asksForASavedSearchFirstWithoutOne() {
		given(this.discovery.findSearches(1)).willReturn(List.of());
		given(this.watchlist.findCompanies(1)).willReturn(List.of());

		assertThat(this.mvc.get().uri("/watchlist")).hasStatusOk()
			.bodyText()
			.contains("Save a search first", "No companies yet")
			// Nothing to check yet
			.doesNotContain("/watchlist/check");
	}

	@Test
	void watchesSeveralCompaniesAtOnceAndKeepsTheLinksItCouldnt() {
		given(this.watchlist.watch(1, "https://jobs.lever.co/acme/1a2b", null)).willReturn(new WatchResult(ACME, true, 3));
		given(this.watchlist.watch(1, "https://boards.greenhouse.io/globex", null))
			.willReturn(new WatchResult(GLOBEX, true, 1));
		given(this.watchlist.watch(1, "https://jobs.lever.co/initech", null)).willReturn(new WatchResult(ACME, false, 0));
		given(this.watchlist.watch(1, "https://www.linkedin.com/jobs/view/1", null)).willThrow(new BusinessRuleException(
				WatchlistErrorCode.NOT_A_BOARD_LINK, "Not a board"));

		assertThat(this.mvc.post()
			.uri("/watchlist")
			.param("links", """
					 https://jobs.lever.co/acme/1a2b
					https://boards.greenhouse.io/globex, https://www.linkedin.com/jobs/view/1
					https://jobs.lever.co/initech https://jobs.lever.co/acme/1a2b""")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Watching Acme, Globex. 4 jobs match your saved searches.", "You already watch Acme.",
					"https://www.linkedin.com/jobs/view/1: JobWarden can’t watch that link",
					">https://www.linkedin.com/jobs/view/1</textarea>")
			.doesNotContain("<html");
	}

	@Test
	void watchesASuggestionUnderItsName() {
		given(this.watchlist.watch(1, "https://job-boards.greenhouse.io/initech", "Initech"))
			.willReturn(new WatchResult(ACME, true, 0));

		assertThat(this.mvc.post().uri("/watchlist").param("links", "https://job-boards.greenhouse.io/initech")
			.param("name", "Initech")).hasStatusOk()
			.bodyText()
			.contains("No jobs match your saved searches yet");
	}

	@Test
	void saysWhyALinkCantBeWatchedInTheProfilesLanguage() {
		given(this.watchlist.watch(1, "https://jobs.lever.co/nobody", null)).willThrow(new BusinessRuleException(
				WatchlistErrorCode.BOARD_NOT_FOUND, "No board", "https://jobs.lever.co/nobody"));

		assertThat(this.mvc.post().uri("/watchlist").param("links", "https://jobs.lever.co/nobody")
			.header("Accept-Language", "de")).hasStatusOk()
			.bodyText()
			.contains("https://jobs.lever.co/nobody: Unter https://jobs.lever.co/nobody gibt es keine Stellenseite: "
					+ "Prüfe den Link.", "Unternehmen, die du beobachtest");
	}

	@Test
	void asksForALinkWhenThereIsNone() {
		assertThat(this.mvc.post().uri("/watchlist").param("links", "  ")).hasStatusOk()
			.bodyText()
			.contains("JobWarden can’t watch that link");
		then(this.watchlist).should(never()).watch(eq(1L), anyString(), eq(null));
	}

	@Test
	void checksTheBoardsNowAndSaysWhatItFound() {
		given(this.watchlist.check(1)).willReturn(new WatchlistCheck(4, 1, 2));

		assertThat(this.mvc.post().uri("/watchlist/check")).hasStatusOk()
			.bodyText()
			.contains("Companies checked: 4. New jobs: 2. One board couldn’t be read.");
	}

	@Test
	void stopsWatchingACompany() {
		assertThat(this.mvc.post().uri("/watchlist/7/delete")).hasStatusOk();

		then(this.watchlist).should().unwatch(1, 7);
	}

	private static WatchedCompany company(long id, String name, Ats ats, String board) {
		return WatchedCompany.builder()
			.id(id)
			.name(name)
			.ats(ats)
			.board(board)
			.boardUrl(ats.boardUrl(board))
			.addedAt(Instant.now())
			.lastCheckedAt(Instant.now())
			.newThisWeek(3)
			.build();
	}

}
