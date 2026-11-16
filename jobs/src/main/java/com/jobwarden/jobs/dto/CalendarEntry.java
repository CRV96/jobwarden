package com.jobwarden.jobs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.ReminderLead;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Something on the calendar: an event of an application's timeline, or one of its follow-up dates.
 *
 * @param time the time of day, when it's known: only events have one
 * @param eventId for an event, its id on the timeline
 * @param status for an event, the status it gave; for a follow-up date, the application's current status
 * @param stageLabel for an event, the company's own step, e.g. Technical interview
 * @param interviewFormat with {@code interviewLocation} and {@code durationMinutes}: only for an interview
 * @param reminders the reminders set on it; none if left out
 */
@Builder
public record CalendarEntry(LocalDate date, @Nullable LocalTime time, CalendarEntryKind kind, @Nullable Long eventId,
		long applicationId, String title, @Nullable String company, ApplicationStatus status,
		@Nullable String stageLabel, @Nullable InterviewFormat interviewFormat, @Nullable String interviewLocation,
		@Nullable Integer durationMinutes, Set<ReminderLead> reminders) {

	/**
	 * The builder leaves out the reminders of an entry that has none.
	 */
	public CalendarEntry {
		reminders = (reminders == null) ? Set.of() : Set.copyOf(reminders);
	}

	public boolean isInterview() {
		return this.kind == CalendarEntryKind.EVENT && this.status == ApplicationStatus.INTERVIEWING;
	}

	/**
	 * @return what a reminder of it is for
	 */
	public ReminderTarget target() {
		return new ReminderTarget(this.applicationId, this.kind, this.eventId);
	}

	/**
	 * @return when it starts, for its reminders: its time, or {@link ReminderLead#ALL_DAY_START} without one
	 */
	public LocalDateTime start() {
		return this.date.atTime((this.time != null) ? this.time : ReminderLead.ALL_DAY_START);
	}

	/**
	 * @return the reminders it can have: those of an entry without a time of day leave out the minutes and hours
	 */
	public List<ReminderLead> reminderChoices() {
		return ReminderLead.choices(this.time == null);
	}

}
