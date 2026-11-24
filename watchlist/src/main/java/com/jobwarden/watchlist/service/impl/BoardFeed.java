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
sealed interface BoardFeed
		permits GreenhouseFeed, LeverFeed, AshbyFeed, SmartRecruitersFeed, WorkableFeed, RecruiteeFeed, PersonioFeed {

	/**
	 * @param server the ATS's API, e.g. https://boards-api.greenhouse.io; for the boards that are hosts of their own,
	 * the board's, e.g. https://acme.recruitee.com
	 * @param board the board's name, encoded for an address
	 */
	URI jobs(URI server, String board);

	/**
	 * @return the property of the answer that holds the jobs; {@code null} when the answer is the list itself. In an
	 * XML answer ({@link #xml}), the name of each job's element.
	 */
	@Nullable String jobsField();

	/**
	 * @param board the board it's on, for the feeds whose jobs don't say their link (Personio's)
	 * @return the job as a posting; {@code null} when it has no title or link, or isn't listed
	 */
	@Nullable Posting posting(JsonNode job, Board board);

	/**
	 * @return whether the answer is XML (Personio's) rather than JSON: each job's element is read as the JSON the
	 * others give ({@link FeedXml})
	 */
	default boolean xml() {
		return false;
	}

	/**
	 * Where the next page of jobs is, for the feeds that list a board's jobs a page at a time (SmartRecruiters'), or in
	 * one language at a time (Personio's). A job on two pages is one posting ({@link Posting#mergedWith}).
	 *
	 * @param pages how many pages were read so far, the one just read too
	 * @param read how many jobs they had
	 * @param onPage how many the one just read had
	 * @return {@code null} when that one was the last; always, for the feeds that answer with every job at once
	 */
	default @Nullable URI next(URI server, String board, int pages, int read, int onPage) {
		return null;
	}

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
