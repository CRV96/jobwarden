package com.jobwarden.jobs.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.InterviewFormat;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Something on the calendar: an event of an application's timeline, or one of its follow-up dates.
 *
 * @param time the time of day, when it's known: only events have one
 * @param status for an event, the status it gave; for a follow-up date, the application's current status
 * @param stageLabel for an event, the company's own step, e.g. Technical interview
 * @param interviewFormat with {@code interviewLocation} and {@code durationMinutes}: only for an interview
 */
@Builder
public record CalendarEntry(LocalDate date, @Nullable LocalTime time, CalendarEntryKind kind, long applicationId,
		String title, @Nullable String company, ApplicationStatus status, @Nullable String stageLabel,
		@Nullable InterviewFormat interviewFormat, @Nullable String interviewLocation,
		@Nullable Integer durationMinutes) {

	public boolean isInterview() {
		return this.kind == CalendarEntryKind.EVENT && this.status == ApplicationStatus.INTERVIEWING;
	}

}
