package com.jobwarden.identity.enums;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {

	USER_NOT_FOUND("IDENTITY-001"),

	USER_NAME_TAKEN("IDENTITY-002"),

	IMAGE_UNREADABLE("IDENTITY-003"),

	IMAGE_TOO_LARGE("IDENTITY-004");

	private final String code;

}
