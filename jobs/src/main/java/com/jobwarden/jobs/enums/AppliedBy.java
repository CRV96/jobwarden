package com.jobwarden.jobs.enums;

/**
 * Who sent an application.
 */
public enum AppliedBy {

	/** The profile itself: on the posting's site, then in JobWarden or with the extension */
	YOU,

	/** Claude, applying for the profile from the Auto-apply page */
	CLAUDE,

	/** The extension's Easy Apply run, on LinkedIn */
	EXTENSION

}
