package com.jobwarden.inbox.service.impl;

import java.time.Instant;
import java.util.List;

import com.jobwarden.inbox.enums.MailProblem;
import org.jspecify.annotations.Nullable;

/**
 * Reads a mailbox's folder. An interface, so the service is tested without one.
 */
interface MailReader {

	/**
	 * Signs in and opens the folder, to check what the profile typed: over TLS from the start, or, for a server that
	 * starts without it, after STARTTLS. Never without TLS.
	 *
	 * @param server where to read; how it turns TLS on, and its certificate, are what this finds out
	 * @throws MailFailure with why, as the server said it
	 */
	Tested test(MailServer server);

	/**
	 * The emails after the last one read, or, the first time (or when the server numbered the folder anew), those
	 * received since then; the newest ones when there are more than that.
	 *
	 * @param uidValidity the folder's numbering the last time, or {@code null} the first time
	 * @param lastUid the last email read, in that numbering
	 * @throws MailFailure with why, as the server said it
	 */
	Fetched fetch(MailServer server, @Nullable Long uidValidity, @Nullable Long lastUid, Instant since, int max);

	/**
	 * How the server could be read, to read it the same way after.
	 *
	 * @param starttls whether it turned TLS on with STARTTLS
	 * @param certificate for a server on this computer, the SHA-256 fingerprint of the certificate it showed
	 */
	record Tested(boolean starttls, @Nullable String certificate) {
	}

	/**
	 * @param uidValidity the folder's numbering now
	 * @param lastUid the last email there is, read or not: where to start next time
	 */
	record Fetched(long uidValidity, long lastUid, List<MailMessage> messages) {
	}

	/**
	 * The mailbox couldn't be read: the server, the sign-in or the folder.
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

}
