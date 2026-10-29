package com.jobwarden.jobs.repository;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
import org.jspecify.annotations.Nullable;

/**
 * A timeline event with what the calendar shows of its application, selected straight into a record.
 */
public record CalendarEvent(LocalDate date, @Nullable LocalTime time, long applicationId, String title,
		@Nullable String company, ApplicationStatus status, @Nullable String stageLabel,
		@Nullable InterviewFormat interviewFormat, @Nullable String interviewLocation,
		@Nullable Integer durationMinutes) {
}
