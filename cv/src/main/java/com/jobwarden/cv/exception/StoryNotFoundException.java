package com.jobwarden.cv.exception;

import com.jobwarden.common.exception.NotFoundException;

/**
 * The profile has no such story in its story bank, or deleted it.
 */
public class StoryNotFoundException extends NotFoundException {

	public StoryNotFoundException(long storyId) {
		super(CvErrorCode.STORY_NOT_FOUND, "No story " + storyId);
	}

}
