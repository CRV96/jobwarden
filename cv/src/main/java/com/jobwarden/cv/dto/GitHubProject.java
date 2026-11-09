package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A repository the profile chose, with what Claude writes a CV project from: its description, its languages and
 * topics, and the start of its README.
 *
 * @param fullName owner/name
 * @param homepage the site it gives, if any
 * @param languages the languages of its code, the most used first
 * @param readme the start of its README, as written; {@code null} when it has none
 */
public record GitHubProject(String fullName, String name, String url, @Nullable String homepage,
		@Nullable String description, List<String> languages, List<String> topics, int stars, boolean fork,
		@Nullable Instant createdAt, @Nullable Instant pushedAt, @Nullable String readme) {
}
