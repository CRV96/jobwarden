package com.jobwarden.web.enums;

import org.jspecify.annotations.Nullable;

/**
 * Where the common mail providers keep their IMAP servers, so the inbox page fills them in. Each needs an app password,
 * made in the provider's security settings; Proton Mail the password of Proton Mail Bridge, which serves the mailbox
 * on this computer. Outlook and Microsoft 365 aren't among them: they let apps in with OAuth alone, which JobWarden
 * doesn't have.
 *
 * @param host the IMAP server; {@code null} for another provider, whose server the profile types
 * @param port its port: IMAP over TLS, or, for Bridge, the port it shows (STARTTLS, which connecting finds out)
 */
public enum MailProvider {

	GMAIL("imap.gmail.com", 993),

	PROTON_BRIDGE("127.0.0.1", 1143),

	ICLOUD("imap.mail.me.com", 993),

	YAHOO("imap.mail.yahoo.com", 993),

	FASTMAIL("imap.fastmail.com", 993),

	GMX("imap.gmx.com", 993),

	OTHER(null, 993);

	private final @Nullable String host;

	private final int port;

	MailProvider(@Nullable String host, int port) {
		this.host = host;
		this.port = port;
	}

	/**
	 * @return the provider with that IMAP server, e.g. as typed in a form that came back; Other for one of none
	 */
	public static MailProvider of(@Nullable String host) {
		for (MailProvider provider : values()) {
			if (provider.host != null && provider.host.equalsIgnoreCase((host != null) ? host.strip() : null)) {
				return provider;
			}
		}
		return OTHER;
	}

	public @Nullable String getHost() {
		return this.host;
	}

	public int getPort() {
		return this.port;
	}

	/**
	 * Whether its server runs on this computer, as Proton Mail Bridge does: signed in to with Bridge's password.
	 */
	public boolean isLocal() {
		return this == PROTON_BRIDGE;
	}

}
