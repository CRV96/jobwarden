package com.jobwarden.hiring.dto;

import org.jspecify.annotations.Nullable;

/**
 * A job a recruiter is the profile's contact for.
 */
public record ContactJob(long applicationId, String title, @Nullable String company) {
}
