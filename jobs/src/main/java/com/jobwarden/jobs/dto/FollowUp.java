package com.jobwarden.jobs.dto;

import java.time.LocalDate;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Who to follow up with about an application, and when. Every field is optional. Build it with {@link #builder()}.
 *
 * @param contactName the person at the company: the recruiter, the hiring manager...
 * @param contactRole what they do, e.g. Recruiter
 * @param contactLink their profile, e.g. on LinkedIn
 * @param referredBy who referred the profile for the job, if anyone
 * @param deadline when applications to the posting close
 * @param followUpOn when to follow up next, e.g. a week after applying
 */
@Builder
public record FollowUp(@Nullable String contactName, @Nullable String contactRole, @Nullable String contactEmail,
		@Nullable String contactPhone, @Nullable String contactLink, @Nullable String referredBy,
		@Nullable LocalDate deadline, @Nullable LocalDate followUpOn) {

	/** Nothing known yet. */
	public static final FollowUp NONE = FollowUp.builder().build();

	/**
	 * @return whether anyone to contact is known
	 */
	public boolean hasContact() {
		return this.contactName != null || this.contactEmail != null || this.contactPhone != null
				|| this.contactLink != null;
	}

}
