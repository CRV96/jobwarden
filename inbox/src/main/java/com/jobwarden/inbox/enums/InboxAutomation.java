package com.jobwarden.inbox.enums;

/**
 * What becomes of the updates found in the profile's emails, each time the mailbox is read.
 */
public enum InboxAutomation {

	/** Each waits for the profile, to apply or dismiss. */
	ASK,

	/** JobWarden applies those it matched to a job; the others wait for the profile. */
	MATCHED,

	/**
	 * JobWarden applies those it matched to a job, and asks Claude to sort the others: to find their job and apply
	 * them, or dismiss what isn't news. What Claude isn't sure of waits for the profile.
	 */
	CLAUDE;

	/**
	 * @return whether JobWarden applies the updates it matched to a job on its own
	 */
	public boolean appliesMatched() {
		return this != ASK;
	}

}
