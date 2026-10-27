package com.jobwarden.discover.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscoverErrorCode implements ErrorCode {

	FOUND_JOB_NOT_FOUND("DISCOVER-001"),
	SAVED_SEARCH_NOT_FOUND("DISCOVER-002"),

	SEARCH_NAME_REQUIRED("DISCOVER-003");

	private final String code;

}
