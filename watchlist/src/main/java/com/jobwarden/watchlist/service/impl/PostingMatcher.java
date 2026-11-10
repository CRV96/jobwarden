package com.jobwarden.watchlist.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Regions;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
import com.jobwarden.search.Words;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Whether a posting fits a saved search, without AI, as each of the search's lists says: one of its terms, when it has
 * any, and none of the excluded ones. Words are compared whole, without minding case or accents.
 * <ul>
 * <li>Titles: every word of a term is in the posting's title, in any order: "Backend Developer" fits "Senior Developer,
 * Backend". Developer, engineer and programmer count as one word.</li>
 * <li>Excluded: no term in the title or the location. The description isn't read for them: "you'll mentor junior
 * engineers" isn't a junior job.</li>
 * <li>Locations: "Remote" fits a remote posting; a region or a country fits a posting in one of its countries, or one
 * open across a region they share ("Remote, EMEA" fits Germany); "Worldwide" fits all; "remote EU" is remote and in
 * the EU; any other term, a city say, must be in the location's words. A posting that names no place ("Remote")
 * fits no region or country.</li>
 * <li>Work types: remote, hybrid or on site, as the feed or the location says; a posting that says none is taken as on
 * site or hybrid, not remote.</li>
 * <li>Employment types: as the feed says (Lever, Ashby); a posting whose feed says none fits any, but "intern" in its
 * title says internship.</li>
 * <li>Skills: one of them in the title or the description; when there's no description to read yet, any.</li>
 * <li>Recency: published within the time, when the feed says when.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PostingMatcher {

	/** What postings call the same job: a Java developer search fits a Java engineer. */
	private static final Set<String> MAKERS = Set.of("developer", "engineer", "programmer");

	private static final String MAKER = "developer";

	private static final Set<String> INTERNSHIP_WORDS = Set.of("intern", "interns", "internship", "trainee",
			"apprentice", "praktikum", "werkstudent");

	/**
	 * @param searches in the order to try them
	 * @return the first search the posting fits
	 */
	static Optional<SavedSearch> firstMatch(Posting posting, List<SavedSearch> searches, Instant now) {
		return searches.stream().filter(search -> matches(posting, search.criteria(), now)).findFirst();
	}

	static boolean matches(Posting posting, SearchCriteria criteria, Instant now) {
		String title = Words.of(posting.title());
		String location = Words.of(posting.location());
		return fitsTitle(title, criteria.titles())
				&& criteria.excludes().stream().noneMatch(term -> Words.has(title, term) || Words.has(location, term))
				&& fitsLocation(posting, location, criteria.locations())
				&& fitsWorkType(workType(posting, location), criteria.workTypes())
				&& fitsEmployment(posting, title, criteria.employmentTypes())
				&& fitsSkills(posting, title, criteria.skills())
				&& isRecent(posting, criteria.recency().age(), now);
	}

	private static boolean fitsTitle(String title, List<String> terms) {
		if (terms.isEmpty()) {
			return true;
		}
		Set<String> words = makersAsOne(title);
		return terms.stream().anyMatch(term -> {
			Set<String> wanted = makersAsOne(Words.of(term));
			return !wanted.isEmpty() && words.containsAll(wanted);
		});
	}

	private static Set<String> makersAsOne(String words) {
		if (words.isBlank()) {
			return Set.of();
		}
		// A title may say a word twice: "Engineering Manager, Engineering Productivity"
		return Arrays.stream(words.strip().split(" ")).map(word -> MAKERS.contains(word) ? MAKER : word)
			.collect(Collectors.toUnmodifiableSet());
	}

	private static boolean fitsLocation(Posting posting, String location, List<String> terms) {
		if (terms.isEmpty()) {
			return true;
		}
		boolean remote = workType(posting, location) == WorkType.REMOTE;
		Set<String> countries = postingCountries(posting);
		return terms.stream().anyMatch(term -> {
			String words = Words.of(term).strip();
			if (words.equals("remote")) {
				return remote;
			}
			if (words.startsWith("remote ")) {
				return remote && fitsPlace(words.substring("remote ".length()), location, countries);
			}
			return fitsPlace(words, location, countries);
		});
	}

	/**
	 * @param place a region, a country, or other words such as a city's name
	 */
	private static boolean fitsPlace(String place, String location, Set<String> countries) {
		Set<String> wanted = Regions.countriesOf(place);
		if (wanted.isEmpty()) {
			return Words.has(location, place);
		}
		return wanted.equals(Regions.EVERYWHERE) || !Collections.disjoint(wanted, countries);
	}

	/** The countries the feed says, and those the location's places are in. */
	private static Set<String> postingCountries(Posting posting) {
		Set<String> named = Regions.countriesIn(posting.location());
		if (posting.countries().isEmpty()) {
			return named;
		}
		if (named.isEmpty()) {
			return posting.countries();
		}
		Set<String> both = new HashSet<>(named);
		both.addAll(posting.countries());
		return both;
	}

	/**
	 * @return as the feed says, else as the location does; {@code null} when neither says
	 */
	static @Nullable WorkType workType(Posting posting, String location) {
		if (posting.workType() != null) {
			return posting.workType();
		}
		if (Words.has(location, "remote") || Words.has(location, "anywhere") || Words.has(location, "home office")) {
			return WorkType.REMOTE;
		}
		if (Words.has(location, "hybrid")) {
			return WorkType.HYBRID;
		}
		return null;
	}

	private static boolean fitsWorkType(@Nullable WorkType type, Set<WorkType> wanted) {
		if (wanted.isEmpty()) {
			return true;
		}
		return (type != null) ? wanted.contains(type)
				: wanted.contains(WorkType.ONSITE) || wanted.contains(WorkType.HYBRID);
	}

	private static boolean fitsEmployment(Posting posting, String title, Set<EmploymentType> wanted) {
		if (wanted.isEmpty()) {
			return true;
		}
		if (posting.employmentStated()) {
			return posting.employmentType() != null && wanted.contains(posting.employmentType());
		}
		boolean internship = INTERNSHIP_WORDS.stream().anyMatch(word -> Words.has(title, word));
		return !internship || wanted.contains(EmploymentType.INTERNSHIP);
	}

	private static boolean fitsSkills(Posting posting, String title, List<String> skills) {
		if (skills.isEmpty() || posting.description() == null) {
			return true;
		}
		String text = title + Words.of(posting.description());
		return skills.stream().anyMatch(skill -> Words.has(text, skill));
	}

	private static boolean isRecent(Posting posting, @Nullable Duration age, Instant now) {
		return age == null || posting.publishedAt() == null || !posting.publishedAt().isBefore(now.minus(age));
	}

}
