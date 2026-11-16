package com.jobwarden.inbox.service.impl;

import com.jobwarden.inbox.enums.MailProblem;
import org.jspecify.annotations.Nullable;

/**
 * The mailbox couldn't be read (MailReader): the server, the sign-in or the folder.
 */
class MailFailure extends RuntimeException {

	private final @Nullable MailProblem problem;

	MailFailure(String message, Throwable cause) {
		this(message, null, cause);
	}

	/**
	 * @param problem what went wrong, when it's one JobWarden can explain
	 */
	MailFailure(String message, @Nullable MailProblem problem, Throwable cause) {
		super(message, cause);
		this.problem = problem;
	}

	@Nullable MailProblem problem() {
		return this.problem;
	}

}
