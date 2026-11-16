package com.jobwarden.inbox.enums;

/**
 * Who applied an email update to its job, or dismissed it.
 */
public enum UpdateDecider {

	/** The profile, on the inbox page. */
	YOU,

	/** JobWarden, on its own, for an update it matched to a job ({@link InboxAutomation#MATCHED}). */
	JOBWARDEN,

	/** Claude, sorting the inbox when the profile asked it to ({@link InboxAutomation#CLAUDE}). */
	CLAUDE

}
