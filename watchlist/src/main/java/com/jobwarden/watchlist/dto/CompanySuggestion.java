package com.jobwarden.watchlist.dto;

import com.jobwarden.watchlist.enums.Ats;

/**
 * A company to watch, from the profile's own jobs: some of their links are on its board.
 *
 * @param link a link to its board, to watch it with
 * @param jobs how many of the profile's jobs are on it
 */
public record CompanySuggestion(String name, Ats ats, String board, String link, int jobs) {
}
