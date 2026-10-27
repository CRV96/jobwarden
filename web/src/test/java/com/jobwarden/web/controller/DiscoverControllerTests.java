package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.SearchCriteria;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(DiscoverController.class)
class DiscoverControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final SavedSearch REMOTE_JAVA = new SavedSearch(3, "Remote Java",
			SearchCriteria.builder().titles(List.of("Java Developer")).locations(List.of("Cluj-Napoca, Romania")).build(),
			Instant.now());

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

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.discovery.findSearches(1)).willReturn(List.of(REMOTE_JAVA));
		given(this.discovery.countToReviewBySearch(1)).willReturn(List.of(new SearchCount(3L, 2), new SearchCount(null, 1)));
		given(this.discovery.findDecided(eq(1L), any(Integer.class))).willReturn(List.of());
	}

	@Test
	void showsTheJobToReview() {
		given(this.discovery.nextToReview(1, null)).willReturn(Optional.of(job()));
		given(this.discovery.countToReview(1, null)).willReturn(3L);

		assertThat(this.mvc.get().uri("/discover")).hasStatusOk()
			.bodyText()
			.contains("Senior Java Developer", "Java and Kafka, fully remote", "data-swipe-card",
					"value=\"SAVED\" data-swipe=\"right\"", "data-open-link=\"https://jobs.lever.co/acme/1\"", "3 to review")
			// The saved search, with its count, and the address that opens it on the search page
			.contains("Remote Java", "href=\"/search?titles=Java%20Developer&amp;locations=Cluj-Napoca%2C%20Romania");
	}

	@Test
	void saysWhenNothingWaits() {
		given(this.discovery.nextToReview(1, null)).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/discover")).hasStatusOk()
			.bodyText()
			.contains("Nothing to review", "href=\"/settings\"")
			.doesNotContain("data-swipe-card");
	}

	@Test
	void showsTheJobsOfOneSearch() {
		given(this.discovery.nextToReview(1, 3L)).willReturn(Optional.of(job()));
		given(this.discovery.countToReview(1, 3L)).willReturn(2L);

		assertThat(this.mvc.get().uri("/discover").param("search", "3")).bodyText()
			.containsPattern("href=\"/discover\\?search=3\"\\s+aria-current=\"true\"")
			.contains("name=\"search\" value=\"3\"");
	}

	@Test
	void answersADecisionWithTheNextJob() {
		given(this.discovery.nextToReview(1, null)).willReturn(Optional.empty());

		assertThat(this.mvc.post().uri("/discover/11/decision").param("decision", "SAVED").header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.startsWith("<div id=\"discover-review\"")
			.contains("Nothing to review");
		then(this.discovery).should().decide(1, 11, Decision.SAVED);
	}

	@Test
	void sendsTheBrowserBackWithoutHtmx() {
		assertThat(this.mvc.post().uri("/discover/11/decision").param("decision", "DISCARDED").param("search", "3"))
			.hasStatus3xxRedirection()
			.hasRedirectedUrl("/discover?search=3");
	}

	@Test
	void savesTheSearchPagesFilters() {
		given(this.discovery.saveSearch(eq(1L), eq("Remote Java"), any())).willReturn(REMOTE_JAVA);

		assertThat(this.mvc.post()
			.uri("/discover/searches")
			.param("name", "Remote Java")
			.param("titles", "Java Developer")
			.param("locations", "Cluj-Napoca, Romania")
			.param("workTypes", "REMOTE")
			.param("platform", "GOOGLE")
			.header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.contains("Saved as “Remote Java”", "href=\"/discover?search=3\"");
		ArgumentCaptor<SearchCriteria> criteria = ArgumentCaptor.forClass(SearchCriteria.class);
		then(this.discovery).should().saveSearch(eq(1L), eq("Remote Java"), criteria.capture());
		assertThat(criteria.getValue().locations()).containsExactly("Cluj-Napoca, Romania");
		// The page sent no sites: the search has none
		assertThat(criteria.getValue().sites()).isEmpty();
	}

	@Test
	void needsAProfile() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/discover")).hasStatus3xxRedirection().hasRedirectedUrl("/profiles");
	}

	private static FoundJob job() {
		return FoundJob.builder()
			.id(11)
			.title("Senior Java Developer")
			.company("Acme")
			.location("Remote, Europe")
			.link("https://jobs.lever.co/acme/1")
			.source("jobs.lever.co")
			.note("Java and Kafka, fully remote")
			.searchId(3L)
			.searchName("Remote Java")
			.decision(Decision.PENDING)
			.foundAt(Instant.now())
			.build();
	}

}
