package com.jobwarden.watchlist.dto;

/**
 * What watching a company did.
 *
 * @param company the company, now watched; as it was, when it was already
 * @param added whether it's new on the watchlist
 * @param newJobs how many of its postings matched a saved search, and went to the AI Assistant page
 */
public record WatchResult(WatchedCompany company, boolean added, int newJobs) {
}
