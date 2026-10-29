package com.jobwarden.discover.dto;

import java.util.List;
import java.util.Set;

import com.jobwarden.search.Recency;
import com.jobwarden.search.WorkType;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What to look for when Claude searches for jobs. Every field is optional. Build it with {@link #builder()}.
 *
 * @param titles job titles; a job matches any of them
 * @param locations countries, cities or regions, e.g. Romania, Remote in Europe
 * @param skills skills the job must ask for
 * @param excludes words a job must not have, e.g. junior
 * @param minSalary as the profile wrote it, e.g. 4,000 EUR a month
 * @param notes anything else, in the profile's words
 * @param useCv whether to match the jobs against the profile's CV too
 * @param maxJobs how many jobs to add at most, from 1 to {@link #MAX_JOBS}
 */
@Builder
public record AiRequirements(List<String> titles, List<String> locations, List<String> skills, List<String> excludes,
		Set<WorkType> workTypes, @Nullable String seniority, @Nullable String minSalary, @Nullable String notes,
		Recency recency, boolean useCv, int maxJobs) {

	public static final int DEFAULT_MAX_JOBS = 15;

	public static final int MAX_JOBS = 50;

	/** Nothing asked for. */
	public static final AiRequirements NONE = AiRequirements.builder().build();

	public AiRequirements {
		titles = (titles != null) ? List.copyOf(titles) : List.of();
		locations = (locations != null) ? List.copyOf(locations) : List.of();
		skills = (skills != null) ? List.copyOf(skills) : List.of();
		excludes = (excludes != null) ? List.copyOf(excludes) : List.of();
		workTypes = (workTypes != null) ? Set.copyOf(workTypes) : Set.of();
		recency = (recency != null) ? recency : Recency.ANY;
		maxJobs = (maxJobs <= 0) ? DEFAULT_MAX_JOBS : Math.min(maxJobs, MAX_JOBS);
	}

}
