package com.jobwarden.cv.entity;

import java.time.Instant;

import com.jobwarden.cv.dto.OutreachDraft;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Messages to one person about one job.
 */
@Entity
@Table(name = "outreach_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachDraftEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private String recipientName;

	private @Nullable String recipientRole;

	private @Nullable String recipientLink;

	private @Nullable String recipientEmail;

	private @Nullable String linkedinNote;

	private @Nullable String emailSubject;

	private @Nullable String emailBody;

	private Instant updatedAt;

	public OutreachDraftEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * Claude wrote to the person again: who they are, and both messages.
	 */
	public void replace(OutreachDraft draft) {
		this.recipientName = draft.recipientName();
		this.recipientRole = draft.recipientRole();
		this.recipientLink = draft.recipientLink();
		this.recipientEmail = draft.recipientEmail();
		changeMessages(draft.linkedinNote(), draft.emailSubject(), draft.emailBody());
	}

	/**
	 * The profile changed the messages: whom they're to stays.
	 */
	public void changeMessages(@Nullable String linkedinNote, @Nullable String emailSubject, @Nullable String emailBody) {
		this.linkedinNote = linkedinNote;
		this.emailSubject = emailSubject;
		this.emailBody = emailBody;
		this.updatedAt = Instant.now();
	}

}
