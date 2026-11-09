package com.jobwarden.inbox.entity;

import java.time.Instant;

import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.jobs.enums.ApplicationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * An email that looks like news about a job, and what became of it.
 */
@Entity
@Table(name = "email_updates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class EmailUpdateEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private String messageId;

	private Instant receivedAt;

	private @Nullable String sender;

	private @Nullable String subject;

	private @Nullable String excerpt;

	// The start of the email, for Claude to tell which job it's about
	private @Nullable String text;

	private @Nullable Long applicationId;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus status;

	@Enumerated(EnumType.STRING)
	private UpdateDecision decision = UpdateDecision.PENDING;

	private @Nullable Instant decidedAt;

	@Enumerated(EnumType.STRING)
	private @Nullable UpdateDecider decidedBy;

	@Builder
	public EmailUpdateEntity(long userId, String messageId, Instant receivedAt, @Nullable String sender,
			@Nullable String subject, @Nullable String excerpt, @Nullable String text, @Nullable Long applicationId,
			ApplicationStatus status) {
		this.userId = userId;
		this.messageId = messageId;
		this.receivedAt = receivedAt;
		this.sender = sender;
		this.subject = subject;
		this.excerpt = excerpt;
		this.text = text;
		this.applicationId = applicationId;
		this.status = status;
	}

	/**
	 * Applied to that job: the one it matched, or another the profile or Claude found it's about.
	 */
	public void applyTo(long applicationId, UpdateDecider by) {
		this.applicationId = applicationId;
		decide(UpdateDecision.APPLIED, by);
	}

	public void decide(UpdateDecision decision, UpdateDecider by) {
		this.decision = decision;
		this.decidedAt = Instant.now();
		this.decidedBy = by;
	}

	/**
	 * Waiting again, e.g. one Claude dismissed that was news after all.
	 */
	public void reopen() {
		this.decision = UpdateDecision.PENDING;
		this.decidedAt = null;
		this.decidedBy = null;
	}

}
