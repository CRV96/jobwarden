package com.jobwarden.watchlist.service.impl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Words;
import org.jspecify.annotations.Nullable;

/**
 * The kind of contract a feed says, in its words: Lever's commitment (Full-time, Permanent, Contract, Intern...),
 * Ashby's employment type (FullTime, PartTime, Intern, Contract, Temporary).
 *
 * @param stated whether it says one, even one there's no {@link EmploymentType} for, such as part-time
 */
record Employment(boolean stated, @Nullable EmploymentType type) {

	private static final Set<String> INTERNSHIP_WORDS = Set.of("intern", "interns", "internship", "trainee",
			"apprentice", "apprenticeship", "praktikum", "werkstudent");

	static Employment of(@Nullable String said) {
		String words = Words.of(said);
		if (words.isBlank()) {
			return new Employment(false, null);
		}
		Set<String> each = Arrays.stream(words.strip().split(" ")).collect(Collectors.toSet());
		String joined = words.replace(" ", "");
		if (each.stream().anyMatch(INTERNSHIP_WORDS::contains)) {
			return new Employment(true, EmploymentType.INTERNSHIP);
		}
		if (joined.contains("parttime")) {
			return new Employment(true, null);
		}
		if (each.contains("freelance") || each.contains("freelancer")) {
			return new Employment(true, EmploymentType.FREELANCE);
		}
		if (each.contains("contract") || each.contains("contractor") || each.contains("temporary")
				|| joined.contains("fixedterm") || joined.contains("shortterm")) {
			return new Employment(true, EmploymentType.CONTRACT);
		}
		if (joined.contains("fulltime") || each.contains("permanent") || each.contains("regular")) {
			return new Employment(true, EmploymentType.FULL_TIME);
		}
		// Anything else, a team's name say, isn't about the contract
		return new Employment(false, null);
	}

}
