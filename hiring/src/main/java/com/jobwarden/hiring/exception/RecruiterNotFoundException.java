package com.jobwarden.hiring.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such recruiter.
 */
public class RecruiterNotFoundException extends NotFoundException {

	public RecruiterNotFoundException(long recruiterId) {
		super(HiringErrorCode.RECRUITER_NOT_FOUND, "Recruiter " + recruiterId + " doesn't exist", recruiterId);
	}

}
