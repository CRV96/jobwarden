package com.jobwarden.capture.dto;

import java.util.List;

import com.jobwarden.apply.dto.CustomAnswer;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What the extension's Easy Apply run fills LinkedIn's forms in with: the contact details from the CV page, and the
 * answers from the Auto-apply page, the profile's own questions among them. Every answer is as the profile gave it:
 * the run leaves a question none of them answers for the user.
 *
 * @param needsSponsorship {@code null} when the profile didn't say
 * @param willingToRelocate {@code null} when the profile didn't say
 * @param declineVoluntary whether to decline the voluntary questions: gender, ethnicity, disability, veteran status
 * @param custom the profile's own questions with their answers, also the ones learned during earlier runs
 */
@Builder
public record FormAnswers(Contact contact, @Nullable String workAuthorization, @Nullable Boolean needsSponsorship,
		@Nullable String noticePeriod, @Nullable String earliestStart, @Nullable String salaryExpectation,
		@Nullable Boolean willingToRelocate, @Nullable String workPreference, @Nullable Integer yearsOfExperience,
		boolean declineVoluntary, List<CustomAnswer> custom) {

	/**
	 * From the CV page; any of them may be missing.
	 */
	@Builder
	public record Contact(@Nullable String fullName, @Nullable String email, @Nullable String phone,
			@Nullable String location, @Nullable String linkedin, @Nullable String github, @Nullable String website) {
	}

}
