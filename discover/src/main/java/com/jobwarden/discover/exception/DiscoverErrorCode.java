package com.jobwarden.discover.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscoverErrorCode implements ErrorCode {

	FOUND_JOB_NOT_FOUND("DISCOVER-001"),
	SAVED_SEARCH_NOT_FOUND("DISCOVER-002"),

	SEARCH_NAME_REQUIRED("DISCOVER-003"),
	AI_REQUEST_NOT_FOUND("DISCOVER-004"),
	AI_REQUEST_FINISHED("DISCOVER-005");

	private final String code;

}
