package com.jobwarden.mcp.dto;

import com.jobwarden.mcp.enums.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * What fetching a posting's page told.
 *
 * @param certain whether the page said it clearly: its job data, a "no longer available", a page that's gone. A page
 * that only loads, without saying, is open but not certainly
 * @param reason why, in a sentence
 * @param httpStatus the last answer's status, if there was one
 * @param finalUrl where the redirects ended, if they went elsewhere
 * @param evidence the words on the page that decided it
 */
public record PostingCheck(String url, Verdict verdict, boolean certain, String reason, @Nullable Integer httpStatus,
		@Nullable String finalUrl, @Nullable String evidence) {
}
