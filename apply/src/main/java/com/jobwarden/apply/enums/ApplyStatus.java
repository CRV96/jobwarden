package com.jobwarden.apply.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a job queued for Claude is.
 */
public enum ApplyStatus {

	/** Waiting for Claude */
	QUEUED,

	/** Claude is on it */
	APPLYING,

	/** Filled in, for the profile to check and send: {@link ApplyMode#REVIEW} */
	FILLED,

	/** Sent; the job is Applied */
	APPLIED,

	/** The form asks something the answers don't say: the question is with it */
	NEEDS_ANSWER,

	/** Not applied to, e.g. the posting is gone, or applying needs an account */
	SKIPPED,

	/** Claude couldn't, e.g. the site kept failing */
	FAILED,

	/** Taken back by the profile */
	CANCELLED;

	/** What Claude reports once it's done with a job */
	public static final Set<ApplyStatus> RESULTS = EnumSet.of(FILLED, APPLIED, NEEDS_ANSWER, SKIPPED, FAILED);

	/**
	 * A job in one of these isn't queued again: Claude is on it, or the profile has to send it or answer the question
	 */
	public static final Set<ApplyStatus> WAITING = EnumSet.of(QUEUED, APPLYING, FILLED, NEEDS_ANSWER);

	/**
	 * @return whether Claude still has to do it
	 */
	public boolean isOpen() {
		return this == QUEUED || this == APPLYING;
	}

	/**
	 * @return whether it can be queued again: Claude stopped short of applying
	 */
	public boolean canRetry() {
		return this == NEEDS_ANSWER || this == SKIPPED || this == FAILED || this == CANCELLED;
	}

}
