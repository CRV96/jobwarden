package com.jobwarden.jobs.enums;

/**
 * What the application list can be sorted by. Applications with the same value stay newest first; those without one
 * (no location, no source) come last in either direction.
 */
public enum ApplicationSort {

	/** When the application was saved. */
	ADDED,

	/** When the application, its timeline included, last changed. */
	MODIFIED,

	TITLE,

	LOCATION,

	/** The site the posting came from. */
	SOURCE,

	/** In the order of {@link ApplicationStatus}: saved first, withdrawn last. */
	STATUS

}
