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

	CSV_WITHOUT_TITLE_COLUMN("JOBS-007"),

	CALENDAR_ENTRY_NOT_FOUND("JOBS-008"),
	REMINDER_NOT_ALLOWED("JOBS-009"),

	/** The event would say again what the one before it says, e.g. Position closed right after Position closed. */
	STATUS_REPEATED("JOBS-010");

	private final String code;

}
