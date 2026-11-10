package com.jobwarden.watchlist.dto;

/**
 * What reading the watched companies' boards did.
 *
 * @param companies how many companies were checked
 * @param failed how many of their boards couldn't be read
 * @param newJobs how many postings matched a saved search, and went to the AI Assistant page
 */
public record WatchlistCheck(int companies, int failed, int newJobs) {

	public static final WatchlistCheck NONE = new WatchlistCheck(0, 0, 0);

	public WatchlistCheck plus(WatchlistCheck other) {
		return new WatchlistCheck(this.companies + other.companies, this.failed + other.failed,
				this.newJobs + other.newJobs);
	}

}
