package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * A degree or a course on the CV.
 *
 * @param start when it started, as the CV shows it, e.g. 2014
 */
public record CvEducation(String degree, @Nullable String school, @Nullable String location, @Nullable String start,
		@Nullable String end, @Nullable String description) {
}
