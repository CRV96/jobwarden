package com.jobwarden.jobs.dto;

import org.jspecify.annotations.Nullable;

/**
 * The person saved as the contact for one of the profile's jobs, with the job.
 *
 * @param link their profile, e.g. on LinkedIn
 */
public record JobContact(long applicationId, String title, @Nullable String company, @Nullable String name,
		@Nullable String role, @Nullable String email, @Nullable String link) {
}
