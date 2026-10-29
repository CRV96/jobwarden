package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * A job on the CV.
 *
 * @param start when it started, as the CV shows it, e.g. 2021 or Mar 2021
 * @param end when it ended, e.g. Present
 * @param description what was done, a line per point
 */
public record CvExperience(String role, @Nullable String company, @Nullable String location, @Nullable String start,
		@Nullable String end, @Nullable String description) {
}
