package com.jobwarden.search;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What to search for. A posting must match one of each list's terms, and none of the excluded ones. Empty lists and
 * sets don't narrow the search.
 *
 * @param sites the domains Google searches, e.g. {@code jobs.lever.co}; only the Google query uses them
 */
@Builder(toBuilder = true)
public record SearchCriteria(List<String> titles, List<String> locations, List<String> skills, List<String> sites,
		Set<WorkType> workTypes, Set<EmploymentType> employmentTypes, List<String> excludes, Recency recency) {

	/**
	 * Without blank terms, and each term once, whatever its case; the sets in the order of their constants.
	 */
	public SearchCriteria {
		titles = terms(titles);
		locations = terms(locations);
		skills = terms(skills);
		sites = terms(sites);
		workTypes = inOrder(workTypes, WorkType.class);
		employmentTypes = inOrder(employmentTypes, EmploymentType.class);
		excludes = terms(excludes);
		recency = Objects.requireNonNullElse(recency, Recency.ANY);
	}

	/**
	 * @return nothing to search for but the default sites, as the page starts
	 */
	public static SearchCriteria withDefaultSites() {
		return builder().sites(SearchSuggestions.DEFAULT_SITES).build();
	}

	private static List<String> terms(@Nullable Collection<String> terms) {
		if (terms == null) {
			return List.of();
		}
		Map<String, String> byLowerCase = new LinkedHashMap<>();
		for (String term : terms) {
			if (term != null && !term.isBlank()) {
				byLowerCase.putIfAbsent(term.strip().toLowerCase(Locale.ROOT), term.strip());
			}
		}
		return List.copyOf(byLowerCase.values());
	}

	private static <E extends Enum<E>> Set<E> inOrder(@Nullable Collection<E> values, Class<E> type) {
		EnumSet<E> set = EnumSet.noneOf(type);
		if (values != null) {
			values.stream().filter(Objects::nonNull).forEach(set::add);
		}
		return Collections.unmodifiableSet(set);
	}

}
