package com.jobwarden.common.logging;

import com.jobwarden.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.CallerBoundaryAware;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * The application's logger: SLF4J plus error codes. Put Lombok's {@code @CustomLog} on a class to get a
 * {@code log} field of this type (set up in the root {@code lombok.config}).
 * <p>
 * Every error has a code, so {@code error} takes the module's {@link ErrorCode} as its first argument.
 * {@code debug}, {@code info} and {@code warn} don't take one.
 * <p>
 * Each class gets its own instance, named after the class, so log levels can still be set per package, e.g.
 * {@code logging.level.com.jobwarden.jobs=DEBUG}. Messages use SLF4J's {@code {}} placeholders, and an exception
 * passed as the last argument is logged with its stack trace:
 * <pre>{@code log.error(JobsErrorCode.SAVE_FAILED, "Could not save application {}", id, exception);}</pre>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobWardenLogger {

	private static final String FQCN = JobWardenLogger.class.getName();

	private final Logger delegate;

	public static JobWardenLogger of(Class<?> type) {
		return new JobWardenLogger(LoggerFactory.getLogger(type));
	}

	public void debug(String format, @Nullable Object... args) {
		event(Level.DEBUG).log(format, args);
	}

	public void info(String format, @Nullable Object... args) {
		event(Level.INFO).log(format, args);
	}

	public void warn(String format, @Nullable Object... args) {
		event(Level.WARN).log(format, args);
	}

	public void error(ErrorCode errorCode, String format, @Nullable Object... args) {
		// In the message for the console, and as its own field for structured (JSON) logs
		event(Level.ERROR).addKeyValue(ErrorCode.PROPERTY_NAME, errorCode.getCode())
			.log("[" + errorCode.getCode() + "] " + format, args);
	}

	private LoggingEventBuilder event(Level level) {
		// Does nothing (and costs next to nothing) when the level is turned off
		LoggingEventBuilder event = this.delegate.atLevel(level);
		// Otherwise Logback would report this class as the caller (class, method, line) instead of the one that logged
		if (event instanceof CallerBoundaryAware callerBoundaryAware) {
			callerBoundaryAware.setCallerBoundary(FQCN);
		}
		return event;
	}

}
