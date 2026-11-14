package com.jobwarden.watchlist.service.impl;

import java.net.URI;

import com.jobwarden.watchlist.enums.Ats;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One ATS's public job board feed (Strategy): where a board's jobs are, how the answer lists them, and how a job reads
 * as a posting. {@link BoardFeeds} does the reading, the same for every feed: the timeouts, the size cap and the
 * deadline. A new ATS is a new {@link Ats}, an implementation here, and its case in {@link BoardFeeds#feed}.
 */
sealed interface BoardFeed permits GreenhouseFeed, LeverFeed, AshbyFeed {

	/**
	 * @param server the ATS's API, e.g. https://boards-api.greenhouse.io
	 * @param board the board's name, encoded for an address
	 */
	URI jobs(URI server, String board);

	/**
	 * @return the property of the answer that holds the jobs; {@code null} when the answer is the list itself
	 */
	@Nullable String jobsField();

	/**
	 * @return the job as a posting; {@code null} when it has no title or link, or isn't listed
	 */
	@Nullable Posting posting(JsonNode job);

	/**
	 * Where one job's description is, for the feeds whose list leaves it out.
	 *
	 * @param posting the posting's id, encoded for an address
	 * @return {@code null} when the list has the descriptions already
	 */
	default @Nullable URI description(URI server, String board, String posting) {
		return null;
	}

	/**
	 * @return the description in the answer {@link #description} points at
	 */
	default @Nullable String descriptionOf(JsonNode job) {
		return null;
	}

}
