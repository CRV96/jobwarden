package com.jobwarden.inbox.dto;

import java.util.Objects;

/**
 * A mailbox to read, over IMAP with TLS.
 *
 * @param username usually the email address
 * @param password an app password, made in the mail provider's security settings for JobWarden alone: most providers
 * refuse the account's own password to apps
 * @param folder where the recruiters' emails arrive, e.g. INBOX
 */
public record NewMailAccount(String host, int port, String username, String password, String folder) {

	public NewMailAccount {
		Objects.requireNonNull(host, "host");
		Objects.requireNonNull(username, "username");
		Objects.requireNonNull(password, "password");
		Objects.requireNonNull(folder, "folder");
		if (port < 1 || port > 65_535) {
			throw new IllegalArgumentException("No port " + port);
		}
	}

}
