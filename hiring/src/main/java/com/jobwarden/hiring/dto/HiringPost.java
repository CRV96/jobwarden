package com.jobwarden.hiring.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A post saying someone's hiring.
 *
 * @param text the post, as posted
 * @param role the job it's for, e.g. Java Developer, when it says
 * @param country where the job is, as an ISO 3166 code, e.g. RO
 * @param tags its hashtags, without the #, in lower case: hiring, java
 * @param postedOn when it was posted, when that's known
 * @param foundAt when Claude, or the profile, added it
 * @param applicationId the job it was saved as, or {@code null}
 */
@Builder
public record HiringPost(long id, String link, String text, @Nullable String role, @Nullable String company,
		@Nullable String country, List<String> tags, @Nullable LocalDate postedOn, Instant foundAt,
		@Nullable Long applicationId, Recruiter recruiter) {

	public HiringPost {
		tags = (tags != null) ? List.copyOf(tags) : List.of();
	}

	/** Longer than this, or with more lines, a post shows its start, and a button for the rest. */
	private static final int LONG_TEXT = 420;

	private static final int LONG_LINES = 8;

	/**
	 * @return whether it's too long to show whole at first
	 */
	public boolean isLong() {
		return this.text.length() > LONG_TEXT || this.text.lines().count() > LONG_LINES;
	}

	/**
	 * @return the day it was posted, or else the day it was found
	 */
	public LocalDate day(ZoneId zone) {
		return (this.postedOn != null) ? this.postedOn : LocalDate.ofInstant(this.foundAt, zone);
	}

}
