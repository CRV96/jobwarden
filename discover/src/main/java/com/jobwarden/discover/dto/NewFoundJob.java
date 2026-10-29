package com.jobwarden.discover.dto;

import java.time.LocalDate;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A job found for a profile, to review on the AI Assistant page. Build it with {@link #builder()}.
 *
 * @param link the posting's address, on the web: a profile has each link once
 * @param note why the job fits the search, in a sentence or two
 * @param postedOn when the posting went up, if it says
 * @param searchId the saved search it was found for, if any
 * @param searchLabel what it was searched for, when it wasn't a saved search, e.g. "remote Java jobs in Europe"
 * @param requestId the request to Claude it was found for, if any
 */
@Builder
public record NewFoundJob(String title, @Nullable String company, @Nullable String location, @Nullable String salary,
		String link, @Nullable String description, @Nullable String note, @Nullable LocalDate postedOn,
		@Nullable Long searchId, @Nullable String searchLabel, @Nullable Long requestId) {

	/**
	 * A builder can't make sure every required field is set, so a missing one fails here, right away.
	 */
	public NewFoundJob {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(link, "link");
	}

}
