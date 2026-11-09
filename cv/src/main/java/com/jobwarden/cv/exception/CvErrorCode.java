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

	NO_GITHUB("CV-009");

	private final String code;

}
