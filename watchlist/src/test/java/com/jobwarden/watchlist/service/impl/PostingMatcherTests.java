package com.jobwarden.watchlist.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.search.EmploymentType;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostingMatcherTests {

	private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

	private static final Posting JAVA_IN_BERLIN = Posting.builder()
		.id("1")
		.title("Senior Java Engineer (m/w/d)")
		.location("Berlin")
		.link("https://jobs.lever.co/acme/1")
		.publishedAt(NOW.minus(2, ChronoUnit.DAYS))
		.build();

	@Test
	void matchesEveryWordOfATitleWithoutCaseAccentsOrWordOrder() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().titles(List.of("java engineer")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().titles(List.of("Kotlin Developer", "Senior Java")))).isTrue();
		assertThat(matches(titled("Développeur Backend"), criteria().titles(List.of("developpeur backend")))).isTrue();
		assertThat(matches(titled("Developer, Backend"), criteria().titles(List.of("Backend Developer")))).isTrue();
		assertThat(matches(titled("JavaScript Engineer"), criteria().titles(List.of("Java Engineer")))).isFalse();
		assertThat(matches(titled("Data Analyst"), criteria().titles(List.of("Data Engineer")))).isFalse();
		// A word twice in the title, or in the term
		assertThat(matches(titled("Engineering Manager, Engineering Productivity"),
				criteria().titles(List.of("Engineering Manager")))).isTrue();
		assertThat(matches(titled("Java Developer"), criteria().titles(List.of("Java Java Developer")))).isTrue();
	}

	@Test
	void takesDevelopersEngineersAndProgrammersForTheSameJob() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().titles(List.of("Java Developer")))).isTrue();
		assertThat(matches(titled("Java Programmer"), criteria().titles(List.of("Java Engineer")))).isTrue();
	}

	@Test
	void leavesOutExcludedWordsInTheTitleOrLocationButNotTheDescription() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().excludes(List.of("senior")))).isFalse();
		assertThat(matches(JAVA_IN_BERLIN, criteria().excludes(List.of("berlin")))).isFalse();
		assertThat(matches(JAVA_IN_BERLIN.toBuilder().description("You'll mentor junior engineers.").build(),
				criteria().excludes(List.of("junior")))).isTrue();
	}

	@Test
	void findsCountriesAndRegionsInTheLocation() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Germany")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("DACH")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Europe")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("EMEA")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Berlin")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Worldwide")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Nordics", "US")))).isFalse();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Munich")))).isFalse();
	}

	@Test
	void takesAPostingOpenAcrossARegionForItsCountries() {
		Posting emea = located("Remote - EMEA");

		assertThat(matches(emea, criteria().locations(List.of("Romania")))).isTrue();
		assertThat(matches(emea, criteria().locations(List.of("Europe")))).isTrue();
		assertThat(matches(emea, criteria().locations(List.of("US")))).isFalse();
		// "Remote" names no place: it may be anywhere, or one country only
		assertThat(matches(located("Remote"), criteria().locations(List.of("Europe")))).isFalse();
	}

	@Test
	void readsTheCountriesTheFeedSays() {
		Posting london = located("HQ").toBuilder().countries(Set.of("GB")).build();

		assertThat(matches(london, criteria().locations(List.of("UK")))).isTrue();
		assertThat(matches(london, criteria().locations(List.of("Europe")))).isTrue();
		assertThat(matches(london, criteria().locations(List.of("remote EU")))).isFalse();
	}

	@Test
	void tellsRemoteFromTheFeedOrTheLocation() {
		Posting remoteRomania = located("Remote, Romania");
		Posting remoteByFeed = located("Bucharest").toBuilder().workType(WorkType.REMOTE).build();

		assertThat(matches(remoteRomania, criteria().locations(List.of("Remote")))).isTrue();
		assertThat(matches(remoteRomania, criteria().locations(List.of("remote EU")))).isTrue();
		assertThat(matches(remoteByFeed, criteria().locations(List.of("remote EU")))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().locations(List.of("Remote")))).isFalse();
		assertThat(matches(remoteRomania, criteria().workTypes(Set.of(WorkType.REMOTE)))).isTrue();
		assertThat(matches(remoteRomania, criteria().workTypes(Set.of(WorkType.ONSITE)))).isFalse();
	}

	@Test
	void takesAPostingThatSaysNothingForOnSiteOrHybridButNotRemote() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().workTypes(Set.of(WorkType.HYBRID)))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().workTypes(Set.of(WorkType.ONSITE)))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().workTypes(Set.of(WorkType.REMOTE)))).isFalse();
		assertThat(matches(located("Berlin (Hybrid)"), criteria().workTypes(Set.of(WorkType.HYBRID)))).isTrue();
		assertThat(matches(located("Berlin (Hybrid)"), criteria().workTypes(Set.of(WorkType.ONSITE)))).isFalse();
	}

	@Test
	void matchesTheEmploymentTypeWhenTheFeedSaysIt() {
		Posting contract = JAVA_IN_BERLIN.toBuilder().employmentStated(true).employmentType(EmploymentType.CONTRACT).build();
		Posting partTime = JAVA_IN_BERLIN.toBuilder().employmentStated(true).build();
		Set<EmploymentType> fullTime = Set.of(EmploymentType.FULL_TIME);

		assertThat(matches(contract, criteria().employmentTypes(Set.of(EmploymentType.CONTRACT)))).isTrue();
		assertThat(matches(contract, criteria().employmentTypes(fullTime))).isFalse();
		assertThat(matches(partTime, criteria().employmentTypes(fullTime))).isFalse();
		assertThat(matches(JAVA_IN_BERLIN, criteria().employmentTypes(fullTime))).isTrue();
		assertThat(matches(titled("Software Engineering Intern"), criteria().employmentTypes(fullTime))).isFalse();
		assertThat(matches(titled("Software Engineering Intern"),
				criteria().employmentTypes(Set.of(EmploymentType.INTERNSHIP)))).isTrue();
	}

	@Test
	void looksForTheSkillsInTheDescriptionOnceThereIsOne() {
		SearchCriteria.SearchCriteriaBuilder kafka = criteria().skills(List.of("Kafka", "RabbitMQ"));

		assertThat(matches(JAVA_IN_BERLIN, kafka)).isTrue();
		assertThat(matches(JAVA_IN_BERLIN.toBuilder().description("You know Kafka and Postgres.").build(), kafka)).isTrue();
		assertThat(matches(JAVA_IN_BERLIN.toBuilder().description("You know Spring and Postgres.").build(), kafka))
			.isFalse();
	}

	@Test
	void leavesOutPostingsOlderThanTheSearchAsks() {
		assertThat(matches(JAVA_IN_BERLIN, criteria().recency(Recency.WEEK))).isTrue();
		assertThat(matches(JAVA_IN_BERLIN, criteria().recency(Recency.DAY))).isFalse();
		assertThat(matches(JAVA_IN_BERLIN.toBuilder().publishedAt(null).build(), criteria().recency(Recency.DAY))).isTrue();
	}

	@Test
	void picksTheFirstSearchThePostingFits() {
		SavedSearch kotlin = new SavedSearch(1, "Kotlin", criteria().titles(List.of("Kotlin")).build(), NOW);
		SavedSearch java = new SavedSearch(2, "Java", criteria().titles(List.of("Java")).build(), NOW);
		SavedSearch german = new SavedSearch(3, "Germany", criteria().locations(List.of("Germany")).build(), NOW);

		assertThat(PostingMatcher.firstMatch(JAVA_IN_BERLIN, List.of(kotlin, java, german), NOW)).contains(java);
		assertThat(PostingMatcher.firstMatch(JAVA_IN_BERLIN, List.of(kotlin), NOW)).isEmpty();
	}

	private static boolean matches(Posting posting, SearchCriteria.SearchCriteriaBuilder criteria) {
		return PostingMatcher.matches(posting, criteria.build(), NOW);
	}

	private static SearchCriteria.SearchCriteriaBuilder criteria() {
		return SearchCriteria.builder();
	}

	private static Posting titled(String title) {
		return JAVA_IN_BERLIN.toBuilder().title(title).build();
	}

	private static Posting located(String location) {
		return JAVA_IN_BERLIN.toBuilder().location(location).build();
	}

}
