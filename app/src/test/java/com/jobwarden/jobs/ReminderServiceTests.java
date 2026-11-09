package com.jobwarden.jobs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Against the real clock: the entries are put at times from now, so a reminder is due, or not yet.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ReminderServiceTests {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private ReminderService reminders;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void listsTheJobsEntriesFromTodayOnWithTheirReminders() {
		long job = create(this.userId);
		long interview = addInterview(job, LocalDateTime.now().plusDays(3));
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder()
			.deadline(TODAY.plusDays(10))
			.followUpOn(TODAY.minusDays(1))
			.build());
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, interview), ReminderLead.HOUR_1, true);

		assertThat(this.reminders.findRemindable(this.userId, job))
			.extracting("kind", "eventId", "reminders")
			// The day to follow up is past, and so is the event of saving it
			.containsExactly(tuple(CalendarEntryKind.EVENT, interview, Set.of(ReminderLead.HOUR_1)),
					tuple(CalendarEntryKind.DEADLINE, null, Set.of()));
	}

	@Test
	void setsAndTakesAwayAReminder() {
		long job = create(this.userId);
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().deadline(TODAY.plusDays(4)).build());
		ReminderTarget deadline = new ReminderTarget(job, CalendarEntryKind.DEADLINE, null);

		this.reminders.set(this.userId, deadline, ReminderLead.DAY_1, true);
		// Twice changes nothing
		this.reminders.set(this.userId, deadline, ReminderLead.DAY_1, true);
		assertThat(this.reminders.findRemindable(this.userId, job)).singleElement()
			.satisfies(entry -> assertThat(entry.reminders()).containsExactly(ReminderLead.DAY_1));

		this.reminders.set(this.userId, deadline, ReminderLead.DAY_1, false);
		assertThat(this.reminders.findRemindable(this.userId, job)).singleElement()
			.satisfies(entry -> assertThat(entry.reminders()).isEmpty());
	}

	@Test
	void refusesMinutesBeforeAnEntryWithoutATimeAndAnEntryThatIsntThere() {
		long job = create(this.userId);
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().deadline(TODAY.plusDays(4)).build());

		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.DEADLINE, null),
					ReminderLead.MINUTES_15, true))
			.satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(JobsErrorCode.REMINDER_NOT_ALLOWED));
		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.FOLLOW_UP, null),
					ReminderLead.DAY_1, true))
			.satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(JobsErrorCode.CALENDAR_ENTRY_NOT_FOUND));
		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.reminders.set(this.otherUserId, new ReminderTarget(job, CalendarEntryKind.DEADLINE, null),
					ReminderLead.DAY_1, true));
	}

	@Test
	void givesAReminderOnceWhenItsDue() {
		long job = create(this.userId);
		long soon = addInterview(job, LocalDateTime.now().plusMinutes(10));
		long later = addInterview(job, LocalDateTime.now().plusDays(3));
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, soon), ReminderLead.MINUTES_15, true);
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, later), ReminderLead.HOUR_1, true);

		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).singleElement().satisfies(notice -> {
			assertThat(notice.applicationId()).isEqualTo(job);
			assertThat(notice.title()).matches("Technical interview · in (9|10) min");
			assertThat(notice.body()).isEqualTo("Backend engineer at Acme · Video call · https://meet.example.com/abc");
		});
		// Taken: a tab or the extension asking next gets nothing, and no other profile ever does
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).isEmpty();
		assertThat(this.reminders.claimDue(this.otherUserId, Locale.ENGLISH)).isEmpty();
	}

	@Test
	void remindsAgainOfAnEntryMovedSinceAndOnceOfSeveralDueTogether() {
		long job = create(this.userId);
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().followUpOn(TODAY.plusDays(1)).build());
		ReminderTarget followUp = new ReminderTarget(job, CalendarEntryKind.FOLLOW_UP, null);
		// Both due already: a week and a day before tomorrow morning
		this.reminders.set(this.userId, followUp, ReminderLead.WEEK_1, true);
		this.reminders.set(this.userId, followUp, ReminderLead.DAY_1, LocalDateTime.now().getHour() >= 9);

		assertThat(this.reminders.claimDue(this.userId, Locale.of("ro"))).singleElement()
			.satisfies(notice -> assertThat(notice.title()).isEqualTo("Revino · mâine"));
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).isEmpty();

		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().followUpOn(TODAY.plusDays(2)).build());
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).singleElement()
			.satisfies(notice -> assertThat(notice.title()).startsWith("Follow up · on "));
	}

	@Test
	void deletesTheRemindersOfAnEntryThatsOverAsTheyreAskedFor() {
		long job = create(this.userId);
		long past = addInterview(job, LocalDateTime.now().minusHours(2));
		long ahead = addInterview(job, LocalDateTime.now().plusDays(3));
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, past), ReminderLead.DAY_1, true);
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, ahead), ReminderLead.HOUR_1, true);

		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).isEmpty();
		assertThat(reminderEvents()).containsExactly(ahead);
	}

	@Test
	void deletesTheRemindersOfEveryProfileWhoseEntryIsOverOrGone() {
		long job = create(this.userId);
		long past = addInterview(job, LocalDateTime.now().minusHours(2));
		long ahead = addInterview(job, LocalDateTime.now().plusDays(3));
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().deadline(TODAY.plusDays(4)).build());
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, past), ReminderLead.DAY_1, true);
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.EVENT, ahead), ReminderLead.HOUR_1, true);
		this.reminders.set(this.userId, new ReminderTarget(job, CalendarEntryKind.DEADLINE, null), ReminderLead.DAY_1, true);
		long othersJob = create(this.otherUserId);
		this.applications.updateFollowUp(this.otherUserId, othersJob, FollowUp.builder().deadline(TODAY.plusDays(2)).build());
		this.reminders.set(this.otherUserId, new ReminderTarget(othersJob, CalendarEntryKind.DEADLINE, null),
				ReminderLead.WEEK_1, true);
		// The deadline cleared since: its reminder has nothing more to say
		this.applications.updateFollowUp(this.otherUserId, othersJob, FollowUp.NONE);

		assertThat(this.reminders.deleteOver()).isEqualTo(2);
		assertThat(reminderEvents()).containsExactlyInAnyOrder(ahead, null);
	}

	private List<Long> reminderEvents() {
		return this.jdbc.sql("SELECT event_id FROM reminders").query((row, number) -> (Long) row.getObject(1, Long.class)).list();
	}

	private long addInterview(long job, LocalDateTime at) {
		LocalDateTime minute = at.truncatedTo(ChronoUnit.MINUTES);
		this.applications.addEvent(this.userId, job, NewTimelineEvent.builder()
			.eventDate(minute.toLocalDate())
			.eventTime(minute.toLocalTime())
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.stageLabel("Technical interview")
			.interviewFormat(InterviewFormat.VIDEO)
			.interviewLocation("https://meet.example.com/abc")
			.build());
		return this.applications.findById(this.userId, job).orElseThrow()
			.timeline()
			.stream()
			.filter(event -> minute.toLocalDate().equals(event.eventDate()) && minute.toLocalTime().equals(event.eventTime()))
			.findFirst()
			.orElseThrow()
			.id();
	}

	private long create(long userId) {
		return this.applications.create(NewApplication.builder()
			.userId(userId)
			.title("Backend engineer")
			.company("Acme")
			.link("https://example.com/jobs/" + System.nanoTime())
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(TODAY.minusDays(3))
			.build());
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id")
			.param(name)
			.query(Long.class)
			.single();
	}

}
