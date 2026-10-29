package com.jobwarden.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(CalendarController.class)
class CalendarControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
	}

	@Test
	void showsTheWholeWeeksOfTheMonthWithWhatHappensOnEachDay() {
		given(this.applications.findCalendar(1, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 11, 1))).willReturn(List.of(
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 10, 30))
					.time(LocalTime.of(14, 0))
					.kind(CalendarEntryKind.EVENT)
					.applicationId(7)
					.title("Backend engineer")
					.company("Acme")
					.status(ApplicationStatus.INTERVIEWING)
					.interviewFormat(InterviewFormat.VIDEO)
					.build(),
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 10, 31))
					.kind(CalendarEntryKind.DEADLINE)
					.applicationId(8)
					.title("Platform engineer")
					.status(ApplicationStatus.SAVED)
					.build()));

		assertThat(this.mvc.get().uri("/calendar").param("month", "2026-10"))
			.hasStatusOk()
			.hasViewName("calendar/calendar")
			.bodyText()
			.contains("October 2026", "/applications/7/drawer", "14:00", "ph-video-camera", "st-interviewing",
					// The job with its company, when it has one
					"Backend engineer at Acme", ">Platform engineer<",
					"/applications/8/drawer", "ph-hourglass-medium",
					// The month before and after, and each day's own view
					"/calendar?view=month&amp;date=2026-09-01", "/calendar?view=month&amp;date=2026-11-01",
					"/calendar?view=day&amp;date=2026-10-30", "data-day-entries", "data-calendar-fit")
			.doesNotContain("time-grid");
	}

	@Test
	void showsAWeekHourByHourFromMonday() {
		given(this.applications.findCalendar(1, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1))).willReturn(List.of(
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 10, 30))
					.time(LocalTime.of(14, 0))
					.kind(CalendarEntryKind.EVENT)
					.applicationId(7)
					.title("Backend engineer")
					.company("Acme")
					.status(ApplicationStatus.INTERVIEWING)
					.stageLabel("Technical interview")
					.interviewFormat(InterviewFormat.VIDEO)
					.durationMinutes(90)
					.build(),
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 10, 31))
					.kind(CalendarEntryKind.DEADLINE)
					.applicationId(8)
					.title("Platform engineer")
					.status(ApplicationStatus.SAVED)
					.build()));

		assertThat(this.mvc.get().uri("/calendar").param("view", "week").param("date", "2026-10-29")).hasStatusOk()
			.bodyText()
			.contains("26 Oct 2026 – 1 Nov 2026", "time-grid", "--days: 7", "All day",
					// At 14:00 for an hour and a half; the deadline above the hours
					"--start: 840; --length: 90; --lane: 0; --lanes: 1", "14:00–15:30", "Backend engineer at Acme",
					"Technical interview · Video call", "title=\"14:00–15:30 Backend engineer at Acme\"",
					"ph-hourglass-medium", "/calendar?view=week&amp;date=2026-10-22", "/calendar?view=week&amp;date=2026-11-05",
					"aria-current=\"page\">Week<")
			.doesNotContain("data-day-entries");
	}

	@Test
	void showsADay() {
		given(this.applications.findCalendar(1, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30))).willReturn(List.of());

		assertThat(this.mvc.get().uri("/calendar").param("view", "DAY").param("date", "2026-10-30")).hasStatusOk()
			.bodyText()
			.contains("Friday, 30 Oct 2026", "--days: 1", "is-day", "/calendar?view=day&amp;date=2026-10-29",
					"/calendar?view=day&amp;date=2026-10-31", "data-scroll-to=\"480\"");
	}

	@Test
	void comesUpWithWhatsAheadNotWhatHappenedToday() {
		LocalDate today = LocalDate.now();
		given(this.applications.findCalendar(eq(1L), any(), any())).willReturn(List.of());
		given(this.applications.findCalendar(1, today, today.plusDays(30))).willReturn(List.of(
				CalendarEntry.builder()
					.date(today)
					.time(LocalTime.of(9, 0))
					.kind(CalendarEntryKind.EVENT)
					.applicationId(7)
					.title("Applied this morning")
					.status(ApplicationStatus.APPLIED)
					.build(),
				CalendarEntry.builder()
					.date(today.plusDays(2))
					.kind(CalendarEntryKind.FOLLOW_UP)
					.applicationId(8)
					.title("Follow up on this one")
					.status(ApplicationStatus.APPLIED)
					.build()));

		assertThat(this.mvc.get().uri("/calendar")).bodyText()
			.contains("Follow up on this one")
			.doesNotContain("Applied this morning");
	}

	@Test
	void showsThisMonthForAnAddressWithoutOne() {
		given(this.applications.findCalendar(eq(1L), any(), any())).willReturn(List.of());

		assertThat(this.mvc.get().uri("/calendar").param("month", "soon")).hasStatusOk()
			.bodyText()
			.contains("Nothing in the next 30 days");
		LocalDate today = LocalDate.now();
		then(this.applications).should().findCalendar(1, today, today.plusDays(30));
	}

	@Test
	void sendsTheBrowserToTheProfilePickerWhenNoProfileIsSelected() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/calendar")).hasRedirectedUrl("/profiles");
	}

}
