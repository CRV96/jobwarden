package com.jobwarden.cv.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CvErrorCode implements ErrorCode {

	CV_FILE_NOT_FOUND("CV-001"),

	FILE_TYPE_NOT_SUPPORTED("CV-002"),
	FILE_TOO_LARGE("CV-003"),
	FILE_EMPTY("CV-004"),
	FILE_NOT_WHAT_IT_SAYS("CV-005"),

	/** GitHub has no user by that name. */
	GITHUB_USER_NOT_FOUND("CV-006"),

	/** GitHub refused the token: mistyped, expired, or revoked. */
	GITHUB_TOKEN_REFUSED("CV-007"),

	/** GitHub couldn't be reached, or answered with an error, e.g. too many requests. */
	GITHUB_NOT_REACHED("CV-008"),

	NO_GITHUB("CV-009"),

	/** The profile has no such story in its story bank, or deleted it. */
	STORY_NOT_FOUND("CV-010"),

	/** The job has no such message, or it was deleted. */
	OUTREACH_DRAFT_NOT_FOUND("CV-011"),

	COVER_LETTER_EMPTY("CV-012"),
	COVER_LETTER_TOO_LONG("CV-013"),

	/** LinkedIn takes at most 300 characters in a connection request's note. */
	LINKEDIN_NOTE_TOO_LONG("CV-014"),

	STORY_WITHOUT_TITLE("CV-015"),
	TOO_MANY_STORIES("CV-016"),

	/** A message must say whom it's to. */
	OUTREACH_WITHOUT_RECIPIENT("CV-017"),

	/** A part of a letter or a message is longer than it can be, e.g. an email's subject. */
	TEXT_TOO_LONG("CV-018");

	private final String code;

}
