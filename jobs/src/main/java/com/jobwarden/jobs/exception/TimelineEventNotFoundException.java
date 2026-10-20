package com.jobwarden.jobs.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The application's timeline has no such event, e.g. it was removed in another tab.
 */
public class TimelineEventNotFoundException extends NotFoundException {

	public TimelineEventNotFoundException(long applicationId, long eventId) {
		// As text, so the message shows 1000 and not 1,000
		super(JobsErrorCode.TIMELINE_EVENT_NOT_FOUND,
				"Timeline event " + eventId + " not found on application " + applicationId, String.valueOf(eventId));
	}

}
