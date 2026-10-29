package com.jobwarden.cv.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has uploaded no CV file, or deleted it.
 */
public class CvFileNotFoundException extends NotFoundException {

	public CvFileNotFoundException(long userId) {
		super(CvErrorCode.CV_FILE_NOT_FOUND, "Profile " + userId + " has no CV file");
	}

}
