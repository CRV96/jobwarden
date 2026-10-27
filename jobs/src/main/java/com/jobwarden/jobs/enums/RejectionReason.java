package com.jobwarden.jobs.enums;

/**
 * Why an application was rejected, so patterns become visible over time. Details go in the event's note.
 */
public enum RejectionReason {

	SALARY_MISMATCH, POSITION_FILLED, LACK_OF_EXPERIENCE, NO_RESPONSE,

	/** A generic rejection email, sent automatically, that gives no reason. */
	AUTOMATED_EMAIL,

	OTHER

}
