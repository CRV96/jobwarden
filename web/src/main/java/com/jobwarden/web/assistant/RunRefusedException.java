package com.jobwarden.web.assistant;

import java.io.Serial;

import lombok.Getter;

/**
 * Run couldn't start the assistant: the page says why, and keeps the prompt to paste.
 */
@Getter
public class RunRefusedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Why: the assistant isn't on this computer (or runs are turned off), it's already running, or it didn't start.
	 */
	public enum Reason {

		UNAVAILABLE, BUSY, FAILED

	}

	private final Reason reason;

	public RunRefusedException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public RunRefusedException(Reason reason, Throwable cause) {
		super(reason.name() + ": " + cause.getMessage(), cause);
		this.reason = reason;
	}

}
