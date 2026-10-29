package com.jobwarden.jobs.dto;

import java.time.Instant;

import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One row of the application list, or one card of the pipeline.
 *
 * @param link the posting's address; {@code null} for one added by hand without a link
 * @param source the site the posting came from, e.g. {@code linkedin.com}; {@code null} for one added by hand
 * without a link
 * @param updatedAt when the application, its timeline included, last changed
 * @param appliedBy who sent the application, when that's known
 */
@Builder
public record ApplicationSummary(
		long id,
		String title,
		@Nullable String company,
		@Nullable String location,
		@Nullable String salary,
		@Nullable String link,
		@Nullable String source,
		ApplicationStatus status,
		Instant capturedAt,
		@Nullable Instant updatedAt,
		@Nullable AppliedBy appliedBy) {

	/**
	 * @return when the application last changed, or when it was saved if that isn't known
	 */
	public Instant lastModified() {
		return (this.updatedAt != null) ? this.updatedAt : this.capturedAt;
	}

}
