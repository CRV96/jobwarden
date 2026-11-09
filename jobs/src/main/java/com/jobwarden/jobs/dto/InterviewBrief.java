package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What Claude found out to prepare the profile for a job's interview. Build it with {@link #builder()}.
 *
 * @param company who they are: what they make, for whom, how big, where, and what's new with them
 * @param role what the job is about, and what they'll look for in the interview
 * @param questions the questions likely asked, technical and about the person
 * @param stories what the person did, from the CV, that answers what they look for: one story each
 * @param askThem good questions to ask them
 * @param watchOut what to look into before saying yes, e.g. layoffs, reviews, a vague salary
 * @param sources where it was read: the company's pages, the news, reviews
 */
@Builder
public record InterviewBrief(String company, @Nullable String role, List<String> questions, List<String> stories,
		List<String> askThem, @Nullable String watchOut, List<String> sources, @Nullable Instant updatedAt) {

	/**
	 * A builder can't make sure every required field is set, so a missing one fails here, right away. The lists left
	 * out are empty.
	 */
	public InterviewBrief {
		Objects.requireNonNull(company, "company");
		questions = (questions == null) ? List.of() : List.copyOf(questions);
		stories = (stories == null) ? List.of() : List.copyOf(stories);
		askThem = (askThem == null) ? List.of() : List.copyOf(askThem);
		sources = (sources == null) ? List.of() : List.copyOf(sources);
	}

}
