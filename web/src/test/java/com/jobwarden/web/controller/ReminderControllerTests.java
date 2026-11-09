package com.jobwarden.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.ReminderNotice;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.ReminderService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest({ ReminderController.class, ReminderNoticeController.class })
class ReminderControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ReminderService reminders;

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
	void listsTheJobsEntriesWithTheirReminders() {
		given(this.reminders.findRemindable(1, 7)).willReturn(List.of(
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 5))
					.time(LocalTime.of(14, 0))
					.kind(CalendarEntryKind.EVENT)
					.eventId(3L)
					.applicationId(7)
					.title("Backend engineer")
					.status(ApplicationStatus.INTERVIEWING)
					.stageLabel("Technical interview")
					.reminders(Set.of(ReminderLead.MINUTES_15))
					.build(),
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 10))
					.kind(CalendarEntryKind.FOLLOW_UP)
					.applicationId(7)
					.title("Backend engineer")
					.status(ApplicationStatus.INTERVIEWING)
					.build()));

		assertThat(this.mvc.get().uri("/applications/7/reminders").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Reminders", "Technical interview", "5 Nov 2026, 14:00", "value=\"MINUTES_15\" checked=\"checked\"",
					"Follow up", "10 Nov 2026", "That morning, 9:00",
					// Loads again when the follow-up dates change
					"follow-up-changed from:body")
			// Only the job's reminders, not the loader they replace
			.doesNotContain("Nothing coming up", "hx-trigger=\"load\"");
	}

	@Test
	void setsTheRemindersTickedAndTakesAwayTheOthers() {
		assertThat(this.mvc.post()
			.uri("/reminders")
			.param("job", "7")
			.param("kind", "EVENT")
			.param("event", "3")
			.param("leads", "HOUR_1", "DAY_1")
			.header("HX-Request", "true")).hasStatus(HttpStatus.NO_CONTENT);

		ReminderTarget interview = new ReminderTarget(7, CalendarEntryKind.EVENT, 3L);
		then(this.reminders).should().set(1, interview, ReminderLead.HOUR_1, true);
		then(this.reminders).should().set(1, interview, ReminderLead.DAY_1, true);
		then(this.reminders).should().set(1, interview, ReminderLead.AT_TIME, false);
		then(this.reminders).should().set(1, interview, ReminderLead.MINUTES_15, false);
		then(this.reminders).should().set(1, interview, ReminderLead.WEEK_1, false);
	}

	@Test
	void takesEveryReminderAwayWhenNoneIsTicked() {
		assertThat(this.mvc.post().uri("/reminders").param("job", "7").param("kind", "DEADLINE")
			.header("HX-Request", "true")).hasStatus(HttpStatus.NO_CONTENT);

		for (ReminderLead lead : ReminderLead.values()) {
			then(this.reminders).should().set(1, new ReminderTarget(7, CalendarEntryKind.DEADLINE, null), lead, false);
		}
	}

	@Test
	void givesTheRemindersDueWithTheJobsPage() {
		given(this.reminders.claimDue(any(Long.class), any())).willReturn(List.of(new ReminderNotice(5, 7,
				CalendarEntryKind.EVENT, "Technical interview · in 15 min", "Backend engineer at Acme · Video call")));

		assertThat(this.mvc.post().uri("/reminders/due")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"id": 5, "title": "Technical interview · in 15 min", "body": "Backend engineer at Acme · Video call",
					  "url": "http://localhost/applications/7"}]
					""");
	}

	@Test
	void givesNoRemindersWithoutAProfile() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.post().uri("/reminders/due")).hasStatusOk().bodyJson().isEqualTo("[]");
		then(this.reminders).should(never()).claimDue(anyLong(), any());
	}

}
