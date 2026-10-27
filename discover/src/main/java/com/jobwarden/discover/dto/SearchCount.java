package com.jobwarden.discover.dto;

import org.jspecify.annotations.Nullable;

/**
 * How many found jobs wait for a decision for one search.
 *
 * @param searchId the saved search; {@code null} for jobs found without one
 */
public record SearchCount(@Nullable Long searchId, long count) {
}
