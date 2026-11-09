package com.jobwarden.inbox.dto;

import java.time.Instant;

import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.MailProblem;
import org.jspecify.annotations.Nullable;

/**
 * The mailbox JobWarden reads a profile's recruiters' emails from, without its password.
 *
 * @param certificate for a server on this computer, such as Proton Mail Bridge, the SHA-256 fingerprint of the one
 * certificate trusted, the one it showed when it was connected; {@code null} for any other
 * @param lastCheckedAt when it was last read; none before the first time
 * @param lastError why it couldn't be read the last time, as the server said it, or {@code null} when it could
 * @param lastProblem what that means, when JobWarden can tell
 * @param readEveryMinutes how often it's read on its own; 0 only when the profile asks
 * @param automation what becomes of the updates found
 */
public record MailAccount(String host, int port, String username, String folder, @Nullable String certificate,
		@Nullable Instant lastCheckedAt, @Nullable String lastError, @Nullable MailProblem lastProblem,
		int readEveryMinutes, InboxAutomation automation) {
}
