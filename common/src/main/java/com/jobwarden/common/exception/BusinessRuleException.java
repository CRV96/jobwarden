package com.jobwarden.common.exception;

import org.jspecify.annotations.Nullable;

/**
 * The request breaks one of the domain's rules, e.g. a profile name that's already taken. Answered with 400.
 * Thrown as is, with the module's code for the rule, so a new rule doesn't need a new exception class:
 * <pre>{@code throw new BusinessRuleException(JobsErrorCode.REJECTION_REASON_NOT_ALLOWED, "...");}</pre>
 */
public class BusinessRuleException extends JobWardenException {

	public BusinessRuleException(ErrorCode errorCode, String message, @Nullable Object... arguments) {
		super(errorCode, message, arguments);
	}

}
