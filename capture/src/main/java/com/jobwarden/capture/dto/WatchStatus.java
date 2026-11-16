package com.jobwarden.capture.dto;

import org.jspecify.annotations.Nullable;

/**
 * The job board a page is on, and whether the profile watches its company.
 *
 * @param name the company's name: as the profile watches it, else as the board's name has it
 * @param system the board's system, as people know it: Greenhouse, Lever or Ashby
 * @param boardUrl where people see the board
 * @param companyId the company on the watchlist, when the profile watches it
 * @param added after watching: whether it's new on the watchlist
 * @param newJobs after watching: how many of its jobs matched a saved search, and went to the AI Assistant page
 */
public record WatchStatus(String name, String system, String boardUrl, boolean watched, @Nullable Long companyId,
		@Nullable Boolean added, @Nullable Integer newJobs) {
}
