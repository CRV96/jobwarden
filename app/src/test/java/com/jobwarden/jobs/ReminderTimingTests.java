package com.jobwarden.jobs;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import com.jobwarden.MutableClock;
import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.convention.TestBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When reminders come and go, to the minute: the application's clock is one these tests set. An interview at 10:00
 * on 20 November 2026, reminded an hour before; ReminderServiceTests has the rest, against the real clock.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ReminderTimingTests {

	private static final LocalDateTime INTERVIEW = LocalDateTime.of(2026, 11, 20, 10, 0);

	private static final MutableClock CLOCK = new MutableClock(INTERVIEW.minusDays(1), ZoneOffset.UTC);

	@TestBean
	private Clock clock;

	@Autowired
	private ReminderService reminders;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long job;

	/** The application's clock, for @TestBean */
	static Clock clock() {
		return CLOCK;
	}

	@BeforeEach
	void interviewWithAReminderAnHourBefore() {
		CLOCK.set(INTERVIEW.minusDays(1));
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = this.jdbc.sql("INSERT INTO users (name) VALUES ('Robert') RETURNING id").query(Long.class).single();
		this.job = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend engineer")
			.company("Acme")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(INTERVIEW.toLocalDate().minusDays(7))
			.build());
		this.applications.addEvent(this.userId, this.job, NewTimelineEvent.builder()
			.eventDate(INTERVIEW.toLocalDate())
			.eventTime(INTERVIEW.toLocalTime())
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.stageLabel("Technical interview")
			.build());
		long interview = this.applications.findById(this.userId, this.job).orElseThrow()
			.timeline()
			.stream()
			.filter(event -> event.stageCategory() == ApplicationStatus.INTERVIEWING)
			.findFirst()
			.orElseThrow()
			.id();
		this.reminders.set(this.userId, new ReminderTarget(this.job, CalendarEntryKind.EVENT, interview),
				ReminderLead.HOUR_1, true);
	}

	@Test
	void comesAnHourBeforeAndNotAMinuteEarlier() {
		CLOCK.set(INTERVIEW.minusMinutes(61));
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).isEmpty();

		CLOCK.set(INTERVIEW.minusMinutes(60));
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).singleElement()
			.satisfies(notice -> assertThat(notice.applicationId()).isEqualTo(this.job));
	}

	@Test
	void comesOnceHoweverOftenItsAskedFor() {
		CLOCK.set(INTERVIEW.minusMinutes(60));
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).hasSize(1);

		CLOCK.set(INTERVIEW.minusMinutes(30));
		assertThat(this.reminders.claimDue(this.userId, Locale.ENGLISH)).isEmpty();
	}

	@Test
	void isDeletedHalfAnHourAfterTheInterviewStarts() {
		CLOCK.set(INTERVIEW.plusMinutes(29));
		assertThat(this.reminders.deleteOver()).isZero();

		CLOCK.set(INTERVIEW.plusMinutes(30));
		assertThat(this.reminders.deleteOver()).isEqualTo(1);
	}

}
