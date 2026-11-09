package com.jobwarden.inbox.enums;

/**
 * What became of an email update.
 */
public enum UpdateDecision {

	/** Waiting for the profile. */
	PENDING,

	/** Added to the job's timeline. */
	APPLIED,

	/** Not news after all. It stays, so reading the email again doesn't bring it back. */
	DISMISSED

}
