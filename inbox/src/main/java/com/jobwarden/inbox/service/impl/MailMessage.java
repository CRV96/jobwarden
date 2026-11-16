package com.jobwarden.inbox.service.impl;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * An email, as much of it as JobWarden reads: who sent it, when, its subject and its text.
 *
 * @param messageId the Message-ID it was sent with, the same in every folder and every time it's read
 * @param text the plain text, or the text of the HTML when there's none; never its attachments
 */
record MailMessage(String messageId, Instant receivedAt, @Nullable String fromName, @Nullable String fromAddress,
		String subject, String text) {
}
