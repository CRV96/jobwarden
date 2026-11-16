package com.jobwarden.cv.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * The profile's own cover letter, written once, that each job's letter starts from: by hand, filled in for the job, or
 * by Claude, who keeps its voice and the paragraphs it always uses.
 *
 * @param body the letter, like {@link CoverLetter#body()}, where {@value #COMPANY} and {@value #ROLE} stand for the
 * job's company and title
 */
public record BaseLetter(String body, Instant updatedAt) {

	/** Stands for the job's company in the letter. */
	public static final String COMPANY = "{company}";

	/** Stands for the job's title in the letter. */
	public static final String ROLE = "{role}";

	/**
	 * @return the letter for one job: its company and title in place of {@value #COMPANY} and {@value #ROLE}; a
	 * placeholder stays when the job doesn't say
	 */
	public String fillIn(@Nullable String company, @Nullable String role) {
		String letter = this.body;
		if (company != null && !company.isBlank()) {
			letter = letter.replace(COMPANY, company.strip());
		}
		if (role != null && !role.isBlank()) {
			letter = letter.replace(ROLE, role.strip());
		}
		return letter;
	}

}
