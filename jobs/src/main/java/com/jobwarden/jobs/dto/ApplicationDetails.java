package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.util.List;

import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Everything shown on an application's detail page.
 *
 * @param notes free text about the application as a whole, e.g. the recruiter's name
 * @param timeline events in the order they happened, oldest first
 */
@Builder
public record ApplicationDetails(
		long id,
		String title,
		@Nullable String company,
		@Nullable String description,
		@Nullable String link,
		@Nullable String location,
		@Nullable String salary,
		@Nullable String employmentType,
		@Nullable String source,
		@Nullable String notes,
		ApplicationStatus status,
		Instant capturedAt,
		List<TimelineEvent> timeline) {
}
