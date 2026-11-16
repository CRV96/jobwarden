package com.jobwarden.jobs;

import java.time.LocalDate;
import java.time.ZoneOffset;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.JobActivity;
import com.jobwarden.jobs.dto.JobActivity.FollowUpDue;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ActivityService;
import com.jobwarden.jobs.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The home page's activity on a day the test chooses, Wednesday 18 March 2026: the service takes the day, so nothing
 * here depends on when the tests run. A week starts on Monday.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ActivityServiceTests {

	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 3, 18);

	@Autowired
	private ActivityService activity;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	@BeforeEach
	void createProfile() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = this.jdbc.sql("INSERT INTO users (name) VALUES ('Robert') RETURNING id").query(Long.class).single();
	}

	@Test
	void countsTheApplicationsOfThisWeekAndTheLastFromMonday() {
		applied("Monday this week", WEDNESDAY.minusDays(2));
		applied("Today", WEDNESDAY);
		applied("Sunday last week", WEDNESDAY.minusDays(3));
		applied("Monday last week", WEDNESDAY.minusDays(9));
		applied("Two weeks ago", WEDNESDAY.minusDays(14));

		JobActivity found = this.activity.find(this.userId, WEDNESDAY, ZoneOffset.UTC);

		assertThat(found.today().applied()).isEqualTo(1);
		assertThat(found.week().applied()).isEqualTo(2);
		assertThat(found.lastWeek().applied()).isEqualTo(2);
		assertThat(found.month().applied()).isEqualTo(5);
	}

	@Test
	void listsTheFollowUpsDueByTodayOfTheJobsStillGoing() {
		long dueToday = applied("Due today", WEDNESDAY.minusDays(10));
		long overdue = applied("Overdue", WEDNESDAY.minusDays(20));
		long tomorrow = applied("Due tomorrow", WEDNESDAY.minusDays(5));
		long ended = applied("Rejected since", WEDNESDAY.minusDays(30), ApplicationStatus.REJECTED);
		followUpOn(dueToday, WEDNESDAY);
		followUpOn(overdue, WEDNESDAY.minusDays(3));
		followUpOn(tomorrow, WEDNESDAY.plusDays(1));
		followUpOn(ended, WEDNESDAY.minusDays(1));

		assertThat(this.activity.find(this.userId, WEDNESDAY, ZoneOffset.UTC).followUps())
			.extracting(FollowUpDue::applicationId)
			.containsExactlyInAnyOrder(dueToday, overdue);
	}

	private long applied(String title, LocalDate on) {
		return applied(title, on, ApplicationStatus.APPLIED);
	}

	private long applied(String title, LocalDate on, ApplicationStatus status) {
		return this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title(title)
			.company("Acme")
			.initialStatus(status)
			.initialStatusDate(on)
			.build());
	}

	private void followUpOn(long job, LocalDate day) {
		this.applications.updateFollowUp(this.userId, job, FollowUp.builder().followUpOn(day).build());
	}

}
