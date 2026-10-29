package com.jobwarden.web.dto;

import java.util.List;
import java.util.Objects;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.CoverLetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The Auto-apply page's answers. A yes-or-no answer left as "Not said" is {@code null}; the custom questions are rows,
 * custom[0].question and custom[0].answer, numbered by app.js as they're added and removed.
 */
public record ApplyAnswersForm(
		@Size(max = ApplyAnswersForm.TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String workAuthorization,
		@Nullable Boolean needsSponsorship,
		@Size(max = ApplyAnswersForm.TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String noticePeriod,
		@Size(max = ApplyAnswersForm.TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String earliestStart,
		@Size(max = ApplyAnswersForm.TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String salaryExpectation,
		@Nullable Boolean willingToRelocate,
		@Size(max = ApplyAnswersForm.TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String workPreference,
		@Min(value = 0, message = "{validation.apply.years}") @Max(value = 60, message = "{validation.apply.years}")
		@Nullable Integer yearsOfExperience,
		@Nullable CoverLetter coverLetter,
		@Nullable Boolean declineVoluntary,
		@Valid @Nullable List<@Nullable CustomRow> custom) {

	/** Also the {@code maxlength} of the one-line fields in apply/apply.html. */
	public static final int TEXT_MAX_LENGTH = 300;

	/** Also the {@code maxlength} of a custom answer. */
	public static final int ANSWER_MAX_LENGTH = 2000;

	public record CustomRow(
			@Size(max = TEXT_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String question,
			@Size(max = ANSWER_MAX_LENGTH, message = "{validation.apply.size}") @Nullable String answer) {
	}

	public ApplicationAnswers toAnswers() {
		return ApplicationAnswers.builder()
			.workAuthorization(this.workAuthorization)
			.needsSponsorship(this.needsSponsorship)
			.noticePeriod(this.noticePeriod)
			.earliestStart(this.earliestStart)
			.salaryExpectation(this.salaryExpectation)
			.willingToRelocate(this.willingToRelocate)
			.workPreference(this.workPreference)
			.yearsOfExperience(this.yearsOfExperience)
			.coverLetter(this.coverLetter)
			.declineVoluntary(Boolean.TRUE.equals(this.declineVoluntary))
			.custom((this.custom == null) ? List.of() : this.custom.stream()
				.filter(Objects::nonNull)
				.map(row -> new CustomAnswer(Objects.toString(row.question(), ""), Objects.toString(row.answer(), "")))
				.toList())
			.build();
	}

}
