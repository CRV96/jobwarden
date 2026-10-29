package com.jobwarden.jobs.enums;

/**
 * What puts an application on the calendar on a day.
 */
public enum CalendarEntryKind {

	/** An event of its timeline: a status it got, or an interview. */
	EVENT,

	/** The day applications to the posting close. */
	DEADLINE,

	/** The day to follow up on it. */
	FOLLOW_UP

}
