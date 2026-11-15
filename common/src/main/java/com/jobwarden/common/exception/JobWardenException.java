package com.jobwarden.common.exception;

import java.util.Arrays;

import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Base class for the errors the modules throw on purpose. Each carries an {@link ErrorCode}, which the exception
 * handlers in {@code web} and {@code capture} log and report. Modules throw one of the subclasses, whose type tells
 * the handlers which HTTP status to answer with.
 * <p>
 * The exception's own message is English, for the logs. Users see the code's message in their language instead
 * (see {@link ErrorCode#getMessageKey()}), with {@link #getArguments()} filling its placeholders.
 */
@Getter
public abstract class JobWardenException extends RuntimeException {

	private final ErrorCode errorCode;

	@Getter(AccessLevel.NONE)
	private final Object[] arguments;

	/**
	 * @param message in English, for the logs; leave out personal data such as names
	 * @param arguments the values for the {@code {0}}, {@code {1}}... placeholders of the code's message; one that's
	 * {@code null} shows as nothing, rather than as "null"
	 */
	protected JobWardenException(ErrorCode errorCode, String message, @Nullable Object... arguments) {
		super(message);
		this.errorCode = errorCode;
		this.arguments = Arrays.stream(arguments).map(argument -> (argument != null) ? argument : "").toArray();
	}

	public Object[] getArguments() {
		return this.arguments.clone();
	}

}
