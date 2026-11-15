package com.jobwarden.common.exception;

import org.jspecify.annotations.Nullable;

/**
 * What the caller asked for doesn't exist, or belongs to another profile. Answered with 404.
 * Each module subclasses it once per resource, e.g. {@code ApplicationNotFoundException}.
 */
public abstract class NotFoundException extends JobWardenException {

	protected NotFoundException(ErrorCode errorCode, String message, @Nullable Object... arguments) {
		super(errorCode, message, arguments);
	}

}
