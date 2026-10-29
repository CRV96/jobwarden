package com.jobwarden.discover.enums;

/**
 * Where a request to Claude stands.
 */
public enum AiRequestStatus {

	/** Asked, and waiting for Claude to pick it up. */
	WAITING,

	/** Claude read it, and is doing it. */
	WORKING,

	DONE,

	/** Claude couldn't do it; its summary says why. */
	FAILED,

	/** Taken back by the profile before it was done. */
	CANCELLED;

	public boolean isOpen() {
		return this == WAITING || this == WORKING;
	}

}
