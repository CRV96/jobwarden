package com.jobwarden.cv.dto;

import java.time.Instant;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Messages to someone about one of the profile's jobs, for the profile to send itself: JobWarden never sends them.
 * Build it with {@link #builder()}.
 *
 * @param id {@code 0} for one not kept yet
 * @param applicationId the job it's about
 * @param recipientName who it's to: the job's recruiter, someone hiring from the Who's hiring page...
 * @param recipientRole what they do, e.g. Technical Recruiter at Acme
 * @param recipientLink their profile, e.g. on LinkedIn, to send the note from
 * @param recipientEmail their email address, when known
 * @param linkedinNote a connection request's note: at most {@link #LINKEDIN_NOTE_MAX_LENGTH} characters
 * @param emailSubject the email's subject
 * @param emailBody the email, from the greeting to the sign-off
 */
@Builder(toBuilder = true)
public record OutreachDraft(long id, long applicationId, String recipientName, @Nullable String recipientRole,
		@Nullable String recipientLink, @Nullable String recipientEmail, @Nullable String linkedinNote,
		@Nullable String emailSubject, @Nullable String emailBody, @Nullable Instant updatedAt) {

	/** The most a LinkedIn connection request's note can have. */
	public static final int LINKEDIN_NOTE_MAX_LENGTH = 300;

	/**
	 * @return how many characters the note has, as LinkedIn counts them: each letter once, whatever its encoding
	 */
	public int linkedinNoteLength() {
		return (this.linkedinNote == null) ? 0 : this.linkedinNote.codePointCount(0, this.linkedinNote.length());
	}

}
