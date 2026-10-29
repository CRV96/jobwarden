package com.jobwarden.jobs.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobsErrorCode implements ErrorCode {

	APPLICATION_NOT_FOUND("JOBS-001"),
	TIMELINE_EVENT_NOT_FOUND("JOBS-004"),

	REJECTION_REASON_NOT_ALLOWED("JOBS-002"),
	CANNOT_MOVE_BACK_TO_SAVED("JOBS-003"),
	CANNOT_REMOVE_LAST_EVENT("JOBS-005"),
	INTERVIEW_DETAILS_NOT_ALLOWED("JOBS-006"),

	CSV_WITHOUT_TITLE_COLUMN("JOBS-007");

	private final String code;

}
