package com.jobwarden.hiring.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such post.
 */
public class HiringPostNotFoundException extends NotFoundException {

	public HiringPostNotFoundException(long postId) {
		super(HiringErrorCode.HIRING_POST_NOT_FOUND, "Hiring post " + postId + " doesn't exist", postId);
	}

}
