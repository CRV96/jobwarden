package com.jobwarden.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.JobActivity.Day;
import com.jobwarden.jobs.dto.JobActivity.Entry;
import com.jobwarden.jobs.dto.JobActivity.FollowUpDue;
import com.jobwarden.jobs.dto.JobActivity.Tally;
import com.jobwarden.jobs.dto.JobActivity;
import com.jobwarden.jobs.dto.JobStatistics;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ActivityService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.StatisticsService;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(HomeController.class)
class HomeControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	// Every page controller extends BaseController, which needs them for the sidebar
	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private ActivityService activity;

	@MockitoBean
	private StatisticsService statistics;

	@BeforeEach
	void robert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.statistics.find(1)).willReturn(new JobStatistics(20, 10, 4, 2, 1, 9, List.of(), List.of(), List.of(),
				List.of(), List.of()));
	}

	@Test
	void showsHowItGoesAndWhatWaits() {
		LocalDate today = LocalDate.now();
		List<Day> days = new ArrayList<>();
		for (int back = JobActivity.DAYS - 1; back >= 0; back--) {
			days.add(new Day(today.minusDays(back), (back == 0) ? 3 : 0, (back == 1) ? 2 : 0));
		}
		given(this.activity.find(eq(1L), eq(today), any())).willReturn(new JobActivity(days, new Tally(3, 0),
				new Tally(3, 2), new Tally(1, 5), new Tally(4, 7), new Tally(2, 9),
				List.of(new Entry(7, "Java Developer", "Acme", ApplicationStatus.INTERVIEWING, "Technical interview", today,
						Instant.now())),
				List.of(new FollowUpDue(8, "Backend Engineer", "Globex", today))));
		given(this.applications.countByStatus(1, null, false))
			.willReturn(Map.of(ApplicationStatus.APPLIED, 6L, ApplicationStatus.OFFER, 1L, ApplicationStatus.SAVED, 0L));
		given(this.applications.findCalendar(eq(1L), eq(today), eq(today.plusDays(HomeController.COMING_UP_DAYS))))
			.willReturn(List.of(CalendarEntry.builder()
				.date(today.plusDays(2))
				.time(LocalTime.of(10, 0))
				.kind(CalendarEntryKind.EVENT)
				.applicationId(7)
				.title("Java Developer")
				.company("Acme")
				.status(ApplicationStatus.INTERVIEWING)
				.stageLabel("Final round")
				.build(), CalendarEntry.builder()
				.date(today)
				.kind(CalendarEntryKind.EVENT)
				.applicationId(9)
				.title("Staff Engineer")
				.status(ApplicationStatus.SAVED)
				.build()));
		given(this.discovery.countToReview(1, null)).willReturn(4L);
		// Every open request, however old: Auto-apply's on its own page
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(openRequest(40, AiRequestKind.SCORE_JOB),
				openRequest(41, AiRequestKind.WRITE_COVER_LETTER), openRequest(42, AiRequestKind.AUTO_APPLY)));
		given(this.discovery.findSearches(1)).willReturn(List.of(new SavedSearch(5, "Java in Bucharest", SearchCriteria.builder()
			.titles(List.of("Java Developer"))
			.locations(List.of("Bucharest"))
			.build(), Instant.now())));

		assertThat(this.mvc.get().uri("/")).hasStatusOk()
			.bodyText()
			// The banner
			.contains("JobWarden", "Your job hunt, kept in one place", "Hi, Robert")
			// Sent today, this week and this month, with what was saved, and the answers
			.contains("Sent today", "Sent this week", "last week: 5", "Sent this month", "last month: 9", "3 jobs saved",
					"40%", "Answers: 4 · interviews: 2", "href=\"/statistics\"")
			// Day by day, each day's count in its tooltip
			.contains("The last 30 days", "Applications sent", "2 in 30 days", "Jobs saved", "3 in 30 days", "Most in a day: 3")
			// What happened lately, and what waits
			.contains("Recent activity", "href=\"/applications/7\"", "Acme · Technical interview", "Interviewing")
			.contains("Waiting for you", "4 jobs to review", "href=\"/discover\"", "1 offer to decide on",
					"2 requests waiting for the assistant", "1 Auto-apply run waiting for you at the browser", "href=\"/apply\"",
					"Follow up on Backend Engineer (Today)", "href=\"/applications/8\"")
			.contains("Coming up", "Final round", "10:00", "Your jobs", "href=\"/pipeline\"")
			// The saved searches: again in the Search Builder, or by the assistant
			.contains("Your searches", "Java in Bucharest", "Java Developer · Bucharest", "Search again", "href=\"/search?",
					"action=\"/discover/searches/5/ask\"", "Ask the AI Assistant")
			// A job saved today isn't ahead
			.doesNotContain("Welcome to JobWarden", "email update", "Staff Engineer");
	}

	@Test
	void welcomesANewProfile() {
		given(this.activity.find(eq(1L), any(), any())).willReturn(new JobActivity(List.of(new Day(LocalDate.now(), 0, 0)),
				Tally.NONE, Tally.NONE, Tally.NONE, Tally.NONE, Tally.NONE, List.of(), List.of()));
		given(this.applications.countByStatus(1, null, false)).willReturn(Map.of());

		assertThat(this.mvc.get().uri("/")).hasStatusOk()
			.bodyText()
			.contains("Welcome to JobWarden", "Add a job", "Nothing in the last 30 days yet.", "All caught up.",
					"Nothing in the next 7 days.", "No saved searches yet");
	}

	private static AiRequest openRequest(long id, AiRequestKind kind) {
		return AiRequest.builder()
			.id(id)
			.kind(kind)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
	}

	@Test
	void asksForAProfileFirst() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/profiles");
	}

}
