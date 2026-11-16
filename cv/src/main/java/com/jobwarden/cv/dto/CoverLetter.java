package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A cover letter for one of the profile's jobs: written by Claude from the CV and the posting, then changed as the
 * profile likes. Its PDF adds the CV page's name and contact details.
 *
 * @param applicationId the job it's for
 * @param body the letter, from the greeting ("Dear Ana,") to the sign-off ("Kind regards,"), paragraphs apart by a
 * blank line, without the name under it
 * @param notes what Claude highlighted, and what to check before sending it
 */
public record CoverLetter(long applicationId, String body, @Nullable String notes, Instant updatedAt) {

	/** The longest letter kept: a cover letter fits on a page. */
	public static final int BODY_MAX_LENGTH = 6000;

	/** The longest notes kept: a few lines on what Claude highlighted. */
	public static final int NOTES_MAX_LENGTH = 2000;

	/**
	 * @return the paragraphs, as the letter's blank lines part them, each with its own line breaks
	 */
	public List<String> paragraphs() {
		return Arrays.stream(this.body.strip().split("\\R\\s*\\R"))
			.map(String::strip)
			.filter(paragraph -> !paragraph.isEmpty())
			.toList();
	}

}
