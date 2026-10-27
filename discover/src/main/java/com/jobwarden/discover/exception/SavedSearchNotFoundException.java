package com.jobwarden.discover.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The saved search doesn't exist, or it belongs to another profile: both look the same to the caller.
 */
public class SavedSearchNotFoundException extends NotFoundException {

	public SavedSearchNotFoundException(long searchId) {
		super(DiscoverErrorCode.SAVED_SEARCH_NOT_FOUND, "Saved search " + searchId + " not found", String.valueOf(searchId));
	}

}
