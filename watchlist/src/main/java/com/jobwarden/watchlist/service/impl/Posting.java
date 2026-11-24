package com.jobwarden.watchlist.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
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

	/** Between a posting's places: "Remote, Canada; Remote, United States". */
	static final String PLACES = "; ";

	Posting {
		countries = (countries == null) ? Set.of() : Set.copyOf(countries);
	}

	/**
	 * The same job, as its feed listed it again: in the other's places too, and with the other's description and pay
	 * when it has none. A feed may list a job once for each of its places (Workable's), twice as its pages move on
	 * (SmartRecruiters'), or once in each language, with a description only in those it was written in (Personio's).
	 */
	Posting mergedWith(Posting other) {
		Set<String> places = new LinkedHashSet<>();
		addPlaces(places, this.location);
		addPlaces(places, other.location);
		Set<String> both = new LinkedHashSet<>(this.countries);
		both.addAll(other.countries);
		return toBuilder().location(places.isEmpty() ? null : String.join(PLACES, places))
			.countries(both)
			.description((this.description != null) ? this.description : other.description)
			.salary((this.salary != null) ? this.salary : other.salary)
			.build();
	}

	private static void addPlaces(Set<String> places, @Nullable String location) {
		if (location != null) {
			places.addAll(Arrays.asList(location.split(PLACES)));
		}
	}

}
