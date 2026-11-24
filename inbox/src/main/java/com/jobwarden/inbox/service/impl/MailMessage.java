package com.jobwarden.inbox.service.impl;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * An email, as much of it as JobWarden reads: who sent it, when, its subject and its text.
 *
 * @param messageId the Message-ID it was sent with, the same in every folder and every time it's read
 * @param text the plain text, or the text of the HTML when there's none; never its attachments
 * @param bulk whether it was sent to many at once, as a newsletter or a mailing list says it (List-Unsubscribe,
 * List-Id, Precedence: bulk): marketing far more often than news of an application
 */
record MailMessage(String messageId, Instant receivedAt, @Nullable String fromName, @Nullable String fromAddress,
		String subject, String text, boolean bulk) {

	/** One sent to the person alone. */
	MailMessage(String messageId, Instant receivedAt, @Nullable String fromName, @Nullable String fromAddress,
			String subject, String text) {
		this(messageId, receivedAt, fromName, fromAddress, subject, text, false);
	}

}
