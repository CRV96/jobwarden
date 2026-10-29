package com.jobwarden.discover.enums;

/**
 * What became of a found job.
 */
public enum Decision {

	/** Waiting on the AI Assistant page. */
	PENDING,

	/** Bookmarked: an application with the status Saved. */
	SAVED,

	/** Applied to: an application with the status Applied. */
	APPLIED,

	/** Not interesting. It stays, so the same posting isn't suggested again. */
	DISCARDED

}
