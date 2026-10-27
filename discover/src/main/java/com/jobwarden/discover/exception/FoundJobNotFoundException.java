package com.jobwarden.discover.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The found job doesn't exist, or it belongs to another profile: both look the same to the caller.
 */
public class FoundJobNotFoundException extends NotFoundException {

	public FoundJobNotFoundException(long foundJobId) {
		// As text, so the message shows 1000 and not 1,000
		super(DiscoverErrorCode.FOUND_JOB_NOT_FOUND, "Found job " + foundJobId + " not found", String.valueOf(foundJobId));
	}

}
