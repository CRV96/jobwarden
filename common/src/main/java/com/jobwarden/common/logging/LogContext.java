package com.jobwarden.common.logging;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.MDC;

/**
 * What ties a log line to the piece of work it's part of: an id in the logging context (SLF4J's MDC), which the log
 * pattern prints ({@code logging.pattern.correlation}) and JSON logs carry as a field. Every request the server answers
 * gets one (web's {@code RequestIdFilter}), and so does every run of a scheduled task, so one id finds every line of
 * one request, or of one check of the mailboxes.
 */
public final class LogContext {

	/** The id's name in the logging context, and so in JSON logs. */
	public static final String ID = "requestId";

	private static final int ID_BYTES = 8;

	private LogContext() {
	}

	/**
	 * @return a new id: 16 hexadecimal digits
	 */
	public static String newId() {
		byte[] random = new byte[ID_BYTES];
		ThreadLocalRandom.current().nextBytes(random);
		return HexFormat.of().formatHex(random);
	}

	/**
	 * @return the task, run with an id of its own in the logging context, e.g. each time a scheduled task runs
	 */
	public static Runnable withNewId(Runnable task) {
		return () -> {
			try (MDC.MDCCloseable _ = MDC.putCloseable(ID, newId())) {
				task.run();
			}
		};
	}

}
