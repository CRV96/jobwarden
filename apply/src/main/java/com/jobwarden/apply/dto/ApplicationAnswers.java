package com.jobwarden.apply.dto;

import java.time.Instant;
import java.util.List;

import com.jobwarden.apply.enums.CoverLetter;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The answers application forms ask for beyond the CV: Claude fills them in as they are, and stops at a question none
 * of them answers. Every field is optional. Build it with {@link #builder()}.
 *
 * @param workAuthorization where the person can work, e.g. EU citizen, can work anywhere in the EU
 * @param needsSponsorship whether they need a visa sponsored; {@code null} when not said
 * @param noticePeriod e.g. 1 month
 * @param earliestStart e.g. 1 December 2026, or 1 month after an offer
 * @param salaryExpectation as the person would write it, e.g. 5,500 EUR gross a month
 * @param willingToRelocate {@code null} when not said
 * @param workPreference e.g. Remote, or hybrid in Bucharest
 * @param declineVoluntary whether to decline the voluntary questions: gender, ethnicity, disability, veteran status
 * @param custom the profile's own questions and answers
 * @param updatedAt when they were last saved; {@code null} before they ever were
 */
@Builder(toBuilder = true)
public record ApplicationAnswers(@Nullable String workAuthorization, @Nullable Boolean needsSponsorship,
		@Nullable String noticePeriod, @Nullable String earliestStart, @Nullable String salaryExpectation,
		@Nullable Boolean willingToRelocate, @Nullable String workPreference, @Nullable Integer yearsOfExperience,
		CoverLetter coverLetter, boolean declineVoluntary, List<CustomAnswer> custom, @Nullable Instant updatedAt) {

	/** Nothing said yet: a cover letter written for each job, and the voluntary questions declined. */
	public static final ApplicationAnswers NONE = ApplicationAnswers.builder().declineVoluntary(true).build();

	public ApplicationAnswers {
		coverLetter = (coverLetter != null) ? coverLetter : CoverLetter.WRITE;
		custom = (custom != null) ? List.copyOf(custom) : List.of();
	}

}
