package com.jobwarden.cv.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such message to send about a job, or deleted it.
 */
public class OutreachDraftNotFoundException extends NotFoundException {

	public OutreachDraftNotFoundException(long draftId) {
		super(CvErrorCode.OUTREACH_DRAFT_NOT_FOUND, "No outreach draft " + draftId);
	}

}
