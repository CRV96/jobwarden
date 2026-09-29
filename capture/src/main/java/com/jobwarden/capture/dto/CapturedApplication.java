package com.jobwarden.capture.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The application JobWarden keeps for a captured link, as the extension shows it: which job, and where it's at.
 *
 * @param statusDate when it got its current status: the date of its latest timeline event
 * @param statusTime that event's time of day, when it's known
 */
@Builder
public record CapturedApplication(
		long applicationId,
		String title,
		@Nullable String company,
		ApplicationStatus status,
		LocalDate statusDate,
		@Nullable LocalTime statusTime) {
}
