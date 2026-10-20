package com.jobwarden.jobs.dto;

import java.time.Instant;

import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One row of the application list, or one card of the pipeline.
 *
 * @param source the site the posting came from, e.g. {@code linkedin.com}; {@code null} for one added by hand
 * without a link
 */
@Builder
public record ApplicationSummary(
		long id,
		String title,
		@Nullable String company,
		@Nullable String location,
		@Nullable String salary,
		@Nullable String source,
		ApplicationStatus status,
		Instant capturedAt) {
}
