package com.jobwarden.inbox.dto;

import java.time.Instant;

import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * An email that looks like news about one of the profile's jobs: waiting to be applied to its timeline or dismissed,
 * or already applied or dismissed.
 *
 * @param sender who sent it, e.g. Ana Pop &lt;ana@acme.com&gt;
 * @param excerpt the words that said what it's about, or the start of the email
 * @param text the start of the email, for Claude to tell which job it's about; {@code null} for one read before it was
 * kept
 * @param applicationId the job it's about, with its title and company; {@code null} when no job matched it, for the
 * profile to pick
 * @param status what the email says: an interview, an offer, a rejection, or that the application arrived
 * @param decidedBy who applied or dismissed it; {@code null} while it waits
 */
@Builder(toBuilder = true)
public record EmailUpdate(long id, Instant receivedAt, @Nullable String sender, @Nullable String subject,
		@Nullable String excerpt, @Nullable String text, @Nullable Long applicationId, @Nullable String jobTitle,
		@Nullable String company, ApplicationStatus status, UpdateDecision decision, @Nullable UpdateDecider decidedBy,
		@Nullable Instant decidedAt) {
}
