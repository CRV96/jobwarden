package com.jobwarden.apply.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplyErrorCode implements ErrorCode {

	APPLY_ITEM_NOT_FOUND("APPLY-001"),

	APPLY_ITEM_FINISHED("APPLY-002"),
	NOT_A_RESULT("APPLY-003");

	private final String code;

}
