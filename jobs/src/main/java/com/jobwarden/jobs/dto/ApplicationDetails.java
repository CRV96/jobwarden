package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.util.List;

import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.AppliedBy;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Everything shown on an application's detail page.
 *
 * @param notes free text about the application as a whole, e.g. the next steps
 * @param followUp who to follow up with, and when; {@link FollowUp#NONE} when nothing is known
 * @param timeline events in the order they happened, oldest first
 * @param appliedBy who sent the application, when that's known
 * @param archivedAt when the profile archived it; {@code null} while it's in use
 */
@Builder(toBuilder = true)
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
		FollowUp followUp,
		ApplicationStatus status,
		Instant capturedAt,
		List<TimelineEvent> timeline,
		@Nullable AppliedBy appliedBy,
		@Nullable Instant archivedAt) {

	public ApplicationDetails {
		followUp = (followUp != null) ? followUp : FollowUp.NONE;
	}

	public boolean isArchived() {
		return this.archivedAt != null;
	}

}
