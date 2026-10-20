package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The entity's own rules, without a database. The service tests in the app module cover the rest.
 */
class ApplicationEntityTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

	@Test
	void statusFollowsTheLatestEvent() {
		ApplicationEntity application = newApplication();

		application.addEvent(event(TODAY, null, ApplicationStatus.APPLIED));
		application.addEvent(event(TODAY.plusDays(2), null, ApplicationStatus.INTERVIEWING));
		application.addEvent(event(TODAY.minusDays(1), null, ApplicationStatus.SAVED));

		assertThat(application.getCurrentStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
		assertThat(application.getEvents()).hasSize(3);
	}

	@Test
	void withinADayTheLaterTimeIsTheLatest() {
		ApplicationEntity application = newApplication();

		application.addEvent(event(TODAY, LocalTime.of(15, 0), ApplicationStatus.INTERVIEWING));
		application.addEvent(event(TODAY, LocalTime.of(9, 0), ApplicationStatus.APPLIED));

		assertThat(application.getCurrentStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
	}

	@Test
	void anEventWithoutATimeCountsAsTheStartOfItsDay() {
		ApplicationEntity application = newApplication();

		application.addEvent(event(TODAY, LocalTime.of(9, 0), ApplicationStatus.APPLIED));
		application.addEvent(event(TODAY, null, ApplicationStatus.SAVED));

		assertThat(application.getCurrentStatus()).isEqualTo(ApplicationStatus.APPLIED);
	}

	@Test
	void eventAddedLastAtTheSameMomentIsTheLatest() {
		ApplicationEntity application = newApplication();

		application.addEvent(event(TODAY, null, ApplicationStatus.APPLIED));
		application.addEvent(event(TODAY, null, ApplicationStatus.REJECTED));

		assertThat(application.getCurrentStatus()).isEqualTo(ApplicationStatus.REJECTED);
	}

	@Test
	void keepsAtLeastOneEventOnTheTimeline() {
		ApplicationEntity application = newApplication();

		application.addEvent(event(TODAY, null, ApplicationStatus.APPLIED));
		assertThat(application.canRemoveEvent()).isFalse();

		application.addEvent(event(TODAY.plusDays(1), null, ApplicationStatus.INTERVIEWING));
		assertThat(application.canRemoveEvent()).isTrue();
	}

	@Test
	void refusesAnEventWithoutItsRequiredFields() {
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> NewTimelineEvent.builder().eventDate(TODAY).build())
			.withMessage("stageCategory");
	}

	@Test
	void timelineChangesOnlyThroughAddEvent() {
		ApplicationEntity application = newApplication();

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> application.getEvents().clear());
	}

	private static ApplicationEntity newApplication() {
		NewApplication application = NewApplication.builder()
			.userId(1)
			.title("Backend engineer")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(TODAY)
			.build();
		return new ApplicationEntity(application, null, Instant.parse("2026-09-28T10:00:00Z"));
	}

	private static NewTimelineEvent event(LocalDate date, @Nullable LocalTime time, ApplicationStatus stageCategory) {
		return NewTimelineEvent.builder().eventDate(date).eventTime(time).stageCategory(stageCategory).build();
	}

}
