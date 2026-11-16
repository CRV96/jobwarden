package com.jobwarden.watchlist.service.impl;

import java.time.Instant;
import java.util.Set;

import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.WorkType;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One job on a board, as its feed tells it: what the matching needs, and what the AI Assistant page shows.
 *
 * @param id the board's id for it, to read its description by, when the list leaves it out (Greenhouse)
 * @param location where it is, all its places together, e.g. "Remote, Canada; Remote, United States"
 * @param countries the countries the feed says it's in, apart from the location's words (Lever, Ashby)
 * @param workType remote, hybrid or on site, when the feed says
 * @param employmentStated whether the feed says what kind of contract it is, even one there's no
 * {@link EmploymentType} for, such as part-time
 * @param employmentType that kind, when there's an {@link EmploymentType} for it
 * @param description its text; {@code null} when the list leaves it out (Greenhouse)
 * @param company the company's name as the board has it, when it has one (Greenhouse)
 */
@Builder(toBuilder = true)
record Posting(String id, String title, @Nullable String location, Set<String> countries,
		@Nullable WorkType workType, boolean employmentStated, @Nullable EmploymentType employmentType, String link,
		@Nullable Instant publishedAt, @Nullable String salary, @Nullable String description, @Nullable String company) {

	Posting {
		countries = (countries == null) ? Set.of() : Set.copyOf(countries);
	}

}
