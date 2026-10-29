package com.jobwarden.apply.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such job queued.
 */
public class ApplyItemNotFoundException extends NotFoundException {

	public ApplyItemNotFoundException(long itemId) {
		super(ApplyErrorCode.APPLY_ITEM_NOT_FOUND, "Apply item " + itemId + " doesn't exist", itemId);
	}

}
