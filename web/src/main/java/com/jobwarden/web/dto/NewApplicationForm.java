package com.jobwarden.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;

import static com.jobwarden.web.dto.FormValues.blankToNull;
import static com.jobwarden.web.dto.FormValues.momentOrNow;

/**
 * An application added by hand, for a job found without the browser extension, e.g. through a referral.
 *
 * @param status with {@code statusDate} and the optional {@code statusTime} start the timeline, so an application
 * sent last week can be added as it was
 * @param rejectionReason required when the status is Rejected, ignored for any other
 */
public record NewApplicationForm(
		@NotBlank(message = "{validation.application.title.required}") String title,
		@Nullable String company,
		@Nullable String location,
		@URL(message = "{validation.application.link.invalid}") @Nullable String link,
		@Nullable String employmentType,
		@Nullable String salary,
		@Nullable String description,
		@NotNull(message = "{validation.application.status.required}") ApplicationStatus status,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate statusDate,
		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME) @Nullable LocalTime statusTime,
		@Nullable RejectionReason rejectionReason) {

	/**
	 * A job added as already rejected says why too, like a rejection added to the timeline (NewEventForm).
	 */
	@AssertTrue(message = "{validation.event.rejectionReason.required}")
	public boolean isReasonGivenForRejection() {
		return this.status == null || !this.status.hasRejectionReason() || this.rejectionReason != null;
	}

	public NewApplication toNewApplication(long userId) {
		FormValues.Moment since = momentOrNow(this.statusDate, this.statusTime);
		return NewApplication.builder()
			.userId(userId)
			.title(this.title.strip())
			.company(blankToNull(this.company))
			.description(blankToNull(this.description))
			.link(blankToNull(this.link))
			.location(blankToNull(this.location))
			.salary(blankToNull(this.salary))
			.employmentType(blankToNull(this.employmentType))
			.initialStatus(this.status)
			.initialStatusDate(since.date())
			.initialStatusTime(since.time())
			.initialRejectionReason(this.status.hasRejectionReason() ? this.rejectionReason : null)
			.build();
	}

}
