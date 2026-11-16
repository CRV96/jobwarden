package com.jobwarden.watchlist.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile doesn't watch the company: it never did, stopped, or it's another profile's.
 */
public class WatchedCompanyNotFoundException extends NotFoundException {

	public WatchedCompanyNotFoundException(long companyId) {
		super(WatchlistErrorCode.WATCHED_COMPANY_NOT_FOUND, "Watched company " + companyId + " not found",
				String.valueOf(companyId));
	}

}
