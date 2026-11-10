package com.jobwarden.watchlist.service;

import java.util.List;

import com.jobwarden.watchlist.dto.CompanySuggestion;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's watchlist.
 */
public interface WatchlistService {

	/**
	 * @return the companies the profile watches, by name
	 */
	List<WatchedCompany> findCompanies(long userId);

	/**
	 * Watches the company whose board the link is on: a link to the board, or to one of its jobs, on Greenhouse,
	 * Lever or Ashby. The board is read at once, to make sure it's there, and its postings that match the profile's
	 * saved searches go to the AI Assistant page. A company already watched stays as it is.
	 *
	 * @param name what to call the company; the board's own name for it, or its name on the board, if {@code null}
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the link isn't on one of those boards, the board
	 * doesn't exist, or it can't be read
	 */
	WatchResult watch(long userId, String link, @Nullable String name);

	/**
	 * Stops watching the company. The jobs found on its board stay on the AI Assistant page.
	 *
	 * @throws com.jobwarden.watchlist.exception.WatchedCompanyNotFoundException if the profile doesn't watch it
	 */
	void unwatch(long userId, long companyId);

	/**
	 * Reads the boards of the companies the profile watches now, rather than in the next few hours.
	 */
	WatchlistCheck check(long userId);

	/**
	 * Companies to watch: those whose boards the profile's jobs are on, the most first, but those it watches.
	 */
	List<CompanySuggestion> suggest(long userId, int limit);

}
