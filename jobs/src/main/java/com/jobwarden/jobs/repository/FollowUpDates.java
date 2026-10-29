package com.jobwarden.jobs.repository;

import java.time.LocalDate;

import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * An application's follow-up dates, with what the calendar shows of it, selected straight into a record.
 */
public record FollowUpDates(long applicationId, String title, @Nullable String company, ApplicationStatus status,
		@Nullable LocalDate deadline, @Nullable LocalDate followUpOn) {
}
