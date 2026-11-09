package com.jobwarden.inbox.service.impl;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Where to read the emails from, and how to sign in: IMAP over TLS, from the start or after STARTTLS.
 *
 * @param starttls whether the server starts without TLS and turns it on when asked, as Proton Mail Bridge does
 * @param certificate for a server on this computer, the certificate it showed when it was connected (its SHA-256
 * fingerprint), the only one trusted after: such a server, like Bridge, signs its own. {@code null} to check it as
 * any other.
 */
record MailServer(String host, int port, String username, String password, String folder, boolean starttls,
		@Nullable String certificate) {

	private static final Set<String> THIS_COMPUTER = Set.of("127.0.0.1", "::1", "[::1]", "localhost");

	MailServer(String host, int port, String username, String password, String folder) {
		this(host, port, username, password, folder, false, null);
	}

	/**
	 * Whether the server runs on this computer, like Proton Mail Bridge: nothing on the way to it to trust.
	 */
	boolean isLocal() {
		return THIS_COMPUTER.contains(this.host.toLowerCase(Locale.ROOT));
	}

	MailServer withStarttls(boolean starttls) {
		return new MailServer(this.host, this.port, this.username, this.password, this.folder, starttls,
				this.certificate);
	}

	@Override
	public String toString() {
		// Never the password, in a log or an exception
		return this.username + "@" + this.host + ":" + this.port + "/" + this.folder;
	}

}
