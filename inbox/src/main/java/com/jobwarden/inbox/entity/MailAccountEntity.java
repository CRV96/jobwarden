package com.jobwarden.inbox.entity;

import java.time.Duration;
import java.time.Instant;

import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.MailProblem;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A profile's mailbox, with its password encrypted, and how far it has been read.
 */
@Entity
@Table(name = "mail_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class MailAccountEntity {

	/** How often a mailbox is read until the profile says otherwise, in minutes. */
	public static final int DEFAULT_READ_EVERY_MINUTES = 10;

	@Id
	private Long userId;

	private String host;

	private int port;

	private String username;

	// Encrypted (SecretBox): never the password as typed
	private String password;

	private String folder;

	// Whether the server turns TLS on with STARTTLS, after starting without it
	private boolean starttls;

	// For a server on this computer: the fingerprint of the one certificate trusted
	private @Nullable String certificate;

	private @Nullable Long uidValidity;

	private @Nullable Long lastUid;

	private @Nullable Instant lastCheckedAt;

	private @Nullable String lastError;

	@Enumerated(EnumType.STRING)
	private @Nullable MailProblem lastProblem;

	// How often it's read on its own; 0 only when the profile asks
	private int readEveryMinutes = DEFAULT_READ_EVERY_MINUTES;

	@Enumerated(EnumType.STRING)
	private InboxAutomation automation = InboxAutomation.ASK;

	private Instant createdAt;

	public MailAccountEntity(long userId) {
		this.userId = userId;
		this.createdAt = Instant.now();
	}

	/**
	 * Another mailbox, or the same with another password: it's read from a few weeks back again.
	 *
	 * @param starttls whether the server turned TLS on with STARTTLS
	 * @param certificate for a server on this computer, the fingerprint of the certificate it showed
	 */
	public void connect(String host, int port, String username, String encryptedPassword, String folder,
			boolean starttls, @Nullable String certificate) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = encryptedPassword;
		this.folder = folder;
		this.starttls = starttls;
		this.certificate = certificate;
		this.uidValidity = null;
		this.lastUid = null;
		this.lastError = null;
		this.lastProblem = null;
	}

	/**
	 * @param uidValidity the folder's numbering, which the UIDs count in
	 * @param lastUid the last email read
	 */
	public void read(long uidValidity, long lastUid, Instant at) {
		this.uidValidity = uidValidity;
		this.lastUid = lastUid;
		this.lastCheckedAt = at;
		this.lastError = null;
		this.lastProblem = null;
	}

	/**
	 * @param error why, as the server said it
	 * @param problem what that means, when JobWarden can tell
	 */
	public void failed(String error, @Nullable MailProblem problem, Instant at) {
		this.lastCheckedAt = at;
		this.lastError = error;
		this.lastProblem = problem;
	}

	/**
	 * @param readEveryMinutes how often it's read on its own; 0 only when the profile asks
	 * @param automation what becomes of the updates found
	 */
	public void changeSettings(int readEveryMinutes, InboxAutomation automation) {
		this.readEveryMinutes = readEveryMinutes;
		this.automation = automation;
	}

	/**
	 * Whether it's time to read it on its own: never read yet, or read (or tried) long enough ago.
	 */
	public boolean isDue(Instant now) {
		return this.readEveryMinutes > 0 && (this.lastCheckedAt == null
				|| !this.lastCheckedAt.plus(Duration.ofMinutes(this.readEveryMinutes)).isAfter(now));
	}

}
