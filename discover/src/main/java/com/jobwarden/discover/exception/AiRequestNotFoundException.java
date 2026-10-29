package com.jobwarden.discover.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The request doesn't exist, or it belongs to another profile: both look the same to the caller.
 */
public class AiRequestNotFoundException extends NotFoundException {

	public AiRequestNotFoundException(long requestId) {
		// As text, so the message shows 1000 and not 1,000
		super(DiscoverErrorCode.AI_REQUEST_NOT_FOUND, "Request " + requestId + " not found", String.valueOf(requestId));
	}

}
