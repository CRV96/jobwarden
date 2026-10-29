package com.jobwarden.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.RejectionReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;

import static com.jobwarden.web.dto.FormValues.blankToNull;
import static com.jobwarden.web.dto.FormValues.momentOrNow;

/**
 * @param eventDate from the add-event form; the status buttons send none, which means now, with the time
 * @param eventTime optional: left out when cleared
 * @param stageLabel only for a status with stages ({@link ApplicationStatus#hasStages()}); ignored for any other,
 * whose form doesn't show the field
 * @param rejectionReason required for a rejection, ignored for any other status
 * @param interviewFormat with {@code interviewLocation} (an address or a meeting link) and {@code durationMinutes}:
 * only for an interview ({@link ApplicationStatus#hasInterviewDetails()}), ignored for any other status
 */
public record NewEventForm(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate eventDate,
		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME) @Nullable LocalTime eventTime,
		@NotNull(message = "{validation.event.stage.required}") ApplicationStatus stageCategory,
		@Size(max = NewEventForm.STAGE_LABEL_MAX_LENGTH, message = "{validation.event.stageLabel.size}")
		@Nullable String stageLabel,
		@Nullable String note,
		@Nullable RejectionReason rejectionReason,
		@Nullable InterviewFormat interviewFormat,
		@Size(max = NewEventForm.INTERVIEW_LOCATION_MAX_LENGTH, message = "{validation.event.interviewLocation.size}")
		@Nullable String interviewLocation,
		@Min(value = 1, message = "{validation.event.duration.range}")
		@Max(value = NewEventForm.DURATION_MAX_MINUTES, message = "{validation.event.duration.range}")
		@Nullable Integer durationMinutes) {

	/** Also the {@code maxlength} of the stage field in applications/fragments.html. */
	public static final int STAGE_LABEL_MAX_LENGTH = 200;

	/** Also the {@code maxlength} of the interview's place field. */
	public static final int INTERVIEW_LOCATION_MAX_LENGTH = 500;

	/** A day: also the {@code max} of the duration field. */
	public static final int DURATION_MAX_MINUTES = 1440;

	/**
	 * A rejection says why, so patterns show up over time. The form asks for it as soon as Rejected is picked.
	 */
	@AssertTrue(message = "{validation.event.rejectionReason.required}")
	public boolean isReasonGivenForRejection() {
		return this.stageCategory == null || !this.stageCategory.hasRejectionReason() || this.rejectionReason != null;
	}

	public NewTimelineEvent toNewTimelineEvent() {
		FormValues.Moment moment = momentOrNow(this.eventDate, this.eventTime);
		return NewTimelineEvent.builder()
			.eventDate(moment.date())
			.eventTime(moment.time())
			.stageCategory(this.stageCategory)
			.stageLabel(this.stageCategory.hasStages() ? blankToNull(this.stageLabel) : null)
			.note(blankToNull(this.note))
			.rejectionReason(this.stageCategory.hasRejectionReason() ? this.rejectionReason : null)
			.interviewFormat(this.stageCategory.hasInterviewDetails() ? this.interviewFormat : null)
			.interviewLocation(this.stageCategory.hasInterviewDetails() ? blankToNull(this.interviewLocation) : null)
			.durationMinutes(this.stageCategory.hasInterviewDetails() ? this.durationMinutes : null)
			.build();
	}

}
