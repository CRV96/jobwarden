package com.jobwarden.hiring.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HiringErrorCode implements ErrorCode {

	RECRUITER_NOT_FOUND("HIRING-001"),
	HIRING_POST_NOT_FOUND("HIRING-002"),

	PROFILE_LINK_NOT_A_WEB_ADDRESS("HIRING-003");

	private final String code;

}
