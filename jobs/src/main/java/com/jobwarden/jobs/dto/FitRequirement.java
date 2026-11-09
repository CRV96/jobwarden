package com.jobwarden.jobs.dto;

import java.util.Objects;

import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import org.jspecify.annotations.Nullable;

/**
 * One of a posting's requirements, and how the CV meets it.
 *
 * @param text the requirement, in the posting's words, e.g. 5+ years of Java
 * @param note what in the CV meets it, or what's missing, in a few words
 */
public record FitRequirement(String text, RequirementImportance importance, RequirementMatch match,
		@Nullable String note) {

	public FitRequirement {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(importance, "importance");
		Objects.requireNonNull(match, "match");
	}

}
