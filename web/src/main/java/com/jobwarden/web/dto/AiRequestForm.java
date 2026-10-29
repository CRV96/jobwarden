package com.jobwarden.web.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.search.Recency;
import com.jobwarden.search.WorkType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A preset of the AI Assistant page, with what to look for. The lists are typed as text, separated by commas.
 *
 * @param kind the preset's button that sent the form
 */
public record AiRequestForm(
		@NotNull(message = "{validation.ai.kind.required}") AiRequestKind kind,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String titles,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String locations,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String skills,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String excludes,
		@Nullable List<WorkType> workTypes,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String seniority,
		@Size(max = AiRequestForm.TEXT_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String minSalary,
		@Size(max = AiRequestForm.NOTES_MAX_LENGTH, message = "{validation.ai.size}") @Nullable String notes,
		@Nullable Recency recency,
		boolean useCv,
		@Min(value = 1, message = "{validation.ai.maxJobs}") @Max(value = AiRequirements.MAX_JOBS, message = "{validation.ai.maxJobs}")
		@Nullable Integer maxJobs) {

	/** Also the {@code maxlength} of the one-line fields in discover/discover.html. */
	public static final int TEXT_MAX_LENGTH = 300;

	public static final int NOTES_MAX_LENGTH = 2000;

	/**
	 * A request about one job is made from the job's page, not with the presets.
	 */
	@AssertTrue(message = "{validation.ai.kind.required}")
	public boolean isPreset() {
		return this.kind == null || !this.kind.isAboutAJob();
	}

	public AiRequirements toRequirements() {
		return AiRequirements.builder()
			.titles(list(this.titles))
			.locations(list(this.locations))
			.skills(list(this.skills))
			.excludes(list(this.excludes))
			.workTypes((this.workTypes == null) ? Set.of() : Set.copyOf(this.workTypes))
			.seniority(FormValues.blankToNull(this.seniority))
			.minSalary(FormValues.blankToNull(this.minSalary))
			.notes(FormValues.blankToNull(this.notes))
			.recency(this.recency)
			.useCv(this.useCv)
			.maxJobs((this.maxJobs != null) ? this.maxJobs : AiRequirements.DEFAULT_MAX_JOBS)
			.build();
	}

	private static List<String> list(@Nullable String text) {
		return (text == null) ? List.of() : Arrays.stream(text.split(",")).map(String::strip).filter(item -> !item.isEmpty()).distinct().toList();
	}

}
