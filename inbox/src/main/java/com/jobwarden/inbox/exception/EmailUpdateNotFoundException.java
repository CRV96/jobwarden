package com.jobwarden.inbox.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such update, or it was applied or dismissed already, e.g. in another tab.
 */
public class EmailUpdateNotFoundException extends NotFoundException {

	public EmailUpdateNotFoundException(long updateId) {
		super(InboxErrorCode.EMAIL_UPDATE_NOT_FOUND, "Email update " + updateId + " doesn't exist", updateId);
	}

}
