package com.jobwarden.web.dto;

import com.jobwarden.jobs.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * An email update, as the profile applies it: to the job it picked, as the status it picked; those the email matched
 * unless changed.
 */
public record EmailUpdateForm(@NotNull(message = "{validation.emailUpdate.job.required}") @Nullable Long job,
		@NotNull(message = "{validation.emailUpdate.status.required}") @Nullable ApplicationStatus status) {

	/** The job the update goes to, once the form is valid. */
	public long requiredJob() {
		return FormValues.validated(this.job, "job");
	}

	/** The job's new status, once the form is valid. */
	public ApplicationStatus requiredStatus() {
		return FormValues.validated(this.status, "status");
	}

}
