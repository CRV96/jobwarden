package com.jobwarden.jobs.entity;

import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One of a job fit's requirements, a row of {@code job_fit_requirements}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class JobFitRequirementEmbeddable {

	private String text;

	@Enumerated(EnumType.STRING)
	private RequirementImportance importance;

	@Enumerated(EnumType.STRING)
	@Column(name = "meets")
	private RequirementMatch match;

	private @Nullable String note;

	public JobFitRequirementEmbeddable(String text, RequirementImportance importance, RequirementMatch match,
			@Nullable String note) {
		this.text = text;
		this.importance = importance;
		this.match = match;
		this.note = note;
	}

}
