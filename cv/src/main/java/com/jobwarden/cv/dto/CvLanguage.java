package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * @param level e.g. Native, C1, Fluent
 */
public record CvLanguage(String language, @Nullable String level) {
}
