package com.jobwarden.cv.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * One of the profile's GitHub repositories, as GitHub lists it.
 *
 * @param fullName owner/name, e.g. CRV96/jobwarden
 * @param language the language most of its code is in, as GitHub tells
 * @param fork whether it's a copy of someone else's
 * @param pushedAt when it was last pushed to
 */
public record GitHubRepository(String fullName, String name, @Nullable String description, @Nullable String language,
		int stars, boolean fork, boolean isPrivate, @Nullable Instant pushedAt, String url) {
}
