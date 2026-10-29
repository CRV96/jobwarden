package com.jobwarden.web.dto;

import java.time.LocalDate;

import com.jobwarden.jobs.dto.FollowUp;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;

import static com.jobwarden.web.dto.FormValues.blankToNull;

/**
 * The contact and follow-up fields of an application, saved as they're changed. Every field is optional.
 */
public record FollowUpForm(
		@Size(max = FollowUpForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String contactName,
		@Size(max = FollowUpForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String contactRole,
		@Email(message = "{validation.followUp.email}")
		@Size(max = FollowUpForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String contactEmail,
		@Size(max = FollowUpForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String contactPhone,
		@Size(max = FollowUpForm.LINK_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String contactLink,
		@Size(max = FollowUpForm.TEXT_MAX_LENGTH, message = "{validation.followUp.size}") @Nullable String referredBy,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate deadline,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate followUpOn) {

	/** Also the {@code maxlength} of the text fields in applications/fragments.html. */
	public static final int TEXT_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of the link field. */
	public static final int LINK_MAX_LENGTH = 500;

	public FollowUp toFollowUp() {
		return FollowUp.builder()
			.contactName(blankToNull(this.contactName))
			.contactRole(blankToNull(this.contactRole))
			.contactEmail(blankToNull(this.contactEmail))
			.contactPhone(blankToNull(this.contactPhone))
			.contactLink(blankToNull(this.contactLink))
			.referredBy(blankToNull(this.referredBy))
			.deadline(this.deadline)
			.followUpOn(this.followUpOn)
			.build();
	}

}
