package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Events the server fires on the page with {@link HtmxHeaders#TRIGGER}. The templates listen with
 * {@code hx-trigger="<event> from:body"}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HtmxEvents {
	/** An application changed in the side panel, so the list or the pipeline behind it reloads. */
	public static final String APPLICATIONS_CHANGED = "applications-changed";
	/** Claude added jobs to review, so the AI Assistant page's card loads again. */
	public static final String FOUND_JOBS_CHANGED = "found-jobs-changed";
	/** A job's contact and follow-up were saved, so its reminders, which list its follow-up dates, load again. */
	public static final String FOLLOW_UP_CHANGED = "follow-up-changed";
	/**
	 * A job's cover letter or messages changed, asking Claude for the letter included, so the Cover letters page's
	 * row of the job loads again.
	 */
	public static final String LETTERS_CHANGED = "letters-changed";
}
