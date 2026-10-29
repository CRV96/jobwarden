package com.jobwarden.market.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Whether the Stack Overflow survey's answers are in.
 *
 * @param year the survey's year, once imported
 * @param figures how many figures were imported
 * @param importing whether it's being downloaded and read right now
 * @param error why the last import failed, when it did
 */
public record SurveyStatus(@Nullable Integer year, long figures, @Nullable Instant importedAt, boolean importing,
		@Nullable String error) {

	public boolean imported() {
		return this.figures > 0;
	}

}
