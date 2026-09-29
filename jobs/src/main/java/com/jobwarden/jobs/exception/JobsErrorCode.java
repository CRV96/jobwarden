package com.jobwarden.jobs.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobsErrorCode implements ErrorCode {

	APPLICATION_NOT_FOUND("JOBS-001"),

	REJECTION_REASON_NOT_ALLOWED("JOBS-002"),
	CANNOT_MOVE_BACK_TO_SAVED("JOBS-003");

	private final String code;

}
