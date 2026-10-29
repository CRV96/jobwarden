package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
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

	@MockitoBean
	private CvService cvs;

	@BeforeEach
	void profile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.discovery.findSearches(1)).willReturn(List.of(REMOTE_JAVA));
		given(this.discovery.countToReviewBySearch(1)).willReturn(List.of(new SearchCount(3L, 2), new SearchCount(null, 1)));
		given(this.discovery.findDecided(eq(1L), any(Integer.class))).willReturn(List.of());
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder().fullName("Robert").build()));
	}

	@Test
	void asksForNothingAboutOneJobFromThePresets() {
		assertThat(this.mvc.post().uri("/discover/requests").param("kind", "TAILOR_CV").param("useCv", "false")).hasStatusOk()
			.bodyText()
			.contains("Pick what Claude should do.");
		then(this.discovery).should(never()).createRequest(anyLong(), any(), any(AiRequirements.class));
	}

	@Test
	void offersThePresetsAndHandsAWaitingRequestToClaudeCode() {
		given(this.discovery.findRequests(eq(1L), any(Integer.class))).willReturn(List.of(
				request(12, AiRequestKind.FIND_JOBS_FOR_CV, AiRequestStatus.WAITING, 0, null),
				request(11, AiRequestKind.CHECK_POSTINGS, AiRequestStatus.DONE, 0, "Checked 9 postings, closed 2.")));

		assertThat(this.mvc.get().uri("/discover")).hasStatusOk()
			.bodyText()
			.contains("AI Assistant", "value=\"FIND_JOBS_FOR_CV\"", "value=\"FOLLOW_UP\"", "Find jobs matching my CV",
					"Waiting for Claude", "Checked 9 postings, closed 2.",
					"Do JobWarden request 12: call get_request with request_id 12 and profile_id 1",
					"claude &quot;Do JobWarden request 12", "/loop 15m Do the waiting JobWarden requests",
					// Open: the list asks again, with how many jobs the card had
					"hx-trigger=\"every 5s\"", "seen")
			.doesNotContain("You have no CV yet");
	}

	@Test
	void needsACvForThePresetThatMatchesIt() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));

		assertThat(this.mvc.get().uri("/discover")).bodyText()
			.contains("You have no CV yet")
			.containsPattern("value=\"FIND_JOBS_FOR_CV\"[^>]*disabled=\"disabled\"");
	}

	@Test
	void asksClaudeWithWhatToLookFor() {
		assertThat(this.mvc.post()
			.uri("/discover/requests")
			.param("kind", "FIND_JOBS")
			.param("titles", "Java Developer, Backend Engineer")
			.param("locations", "Romania, Remote in Europe")
			.param("workTypes", "REMOTE", "HYBRID")
			.param("minSalary", " 4,000 EUR a month ")
			.param("recency", "WEEK")
			.param("useCv", "true")
			.param("maxJobs", "20")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.startsWith("<div id=\"ai-requests\"");
		then(this.discovery).should().createRequest(1, AiRequestKind.FIND_JOBS, AiRequirements.builder()
			.titles(List.of("Java Developer", "Backend Engineer"))
			.locations(List.of("Romania", "Remote in Europe"))
			.workTypes(Set.of(WorkType.REMOTE, WorkType.HYBRID))
			.minSalary("4,000 EUR a month")
			.recency(Recency.WEEK)
			.useCv(true)
			.maxJobs(20)
			.build());
	}

	@Test
	void reloadsTheCardWhenClaudeAddedJobs() {
		given(this.discovery.findRequests(eq(1L), any(Integer.class)))
			.willReturn(List.of(request(12, AiRequestKind.FIND_JOBS_FOR_CV, AiRequestStatus.DONE, 2, "Added 2 jobs.")));
		given(this.discovery.findFoundJobsOfRequest(1, 12)).willReturn(List.of(job()));
		given(this.discovery.countToReview(1, null)).willReturn(5L);

		assertThat(this.mvc.get().uri("/discover/requests").param("seen", "3").header("HX-Request", "true")).hasStatusOk()
			.satisfies(result -> assertThat(result).headers().hasValue("HX-Trigger", "found-jobs-changed"))
			.bodyText()
			.contains("Found 2 jobs", "Senior Java Developer", "Java and Kafka, fully remote")
			// Nothing open: it stops asking
			.doesNotContain("every 5s");
		assertThat(this.mvc.get().uri("/discover/requests").param("seen", "5").header("HX-Request", "true"))
			.headers()
			.doesNotContainHeader("HX-Trigger");
	}

	@Test
	void cancelsARequest() {
		assertThat(this.mvc.post().uri("/discover/requests/12/cancel").header("HX-Request", "true")).hasStatusOk();
		then(this.discovery).should().cancelRequest(1, 12);
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

	private static AiRequest request(long id, AiRequestKind kind, AiRequestStatus status, long found, String summary) {
		return AiRequest.builder()
			.id(id)
			.kind(kind)
			.requirements(AiRequirements.NONE)
			.status(status)
			.summary(summary)
			.foundJobs(found)
			.createdAt(Instant.now())
			.build();
	}

}
