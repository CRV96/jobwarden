package com.jobwarden.jobs.enums;

/**
 * How an interview takes place. Which round it is (HR, technical...) is the event's stage label.
 */
public enum InterviewFormat {

	PHONE, VIDEO, ON_SITE,

	/** A task done at home and handed in, e.g. a coding exercise. */
	TAKE_HOME

}
