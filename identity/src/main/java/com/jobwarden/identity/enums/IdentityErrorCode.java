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

	IMAGE_TOO_LARGE("IDENTITY-004"),

	PASSWORD_WRONG("IDENTITY-005"),

	PASSWORD_TOO_SHORT("IDENTITY-006"),

	PASSWORD_TOO_LONG("IDENTITY-007"),

	PASSWORD_TRIES_EXCEEDED("IDENTITY-008");

	private final String code;

}
