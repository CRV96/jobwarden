package com.jobwarden.search;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryGeneratorTests {

	private final QueryGenerator generator = new QueryGenerator();

	private static final SearchCriteria JAVA_IN_EUROPE = SearchCriteria.builder()
		.titles(List.of("Java Developer", "Backend Engineer"))
		.skills(List.of("Spring"))
		.locations(List.of("Europe"))
		.workTypes(Set.of(WorkType.REMOTE))
		.employmentTypes(Set.of(EmploymentType.CONTRACT, EmploymentType.FULL_TIME))
		.excludes(List.of("junior", "team lead"))
		.sites(List.of("jobs.lever.co", "https://boards.greenhouse.io/"))
		.build();

	@Test
	void searchesTheSitesForEachGroupOfAlternatives() {
		GeneratedQueries queries = this.generator.generate(JAVA_IN_EUROPE);

		assertThat(queries.get(Platform.GOOGLE).query()).isEqualTo(
				"(site:jobs.lever.co OR site:boards.greenhouse.io) (\"Java Developer\" OR \"Backend Engineer\") Spring "
						+ "(full-time OR contract) remote Europe -junior -\"team lead\"");
		assertThat(queries.googleWords()).isEqualTo(17);
		assertThat(queries.googleTruncated()).isFalse();
	}

	@Test
	void givesLinkedInExplicitOperators() {
		assertThat(this.generator.generate(JAVA_IN_EUROPE).get(Platform.LINKEDIN).query()).isEqualTo(
				"(\"Java Developer\" OR \"Backend Engineer\") AND Spring AND (full-time OR contract) AND remote AND Europe "
						+ "NOT junior NOT \"team lead\"");
	}

	@Test
	void looksForPostsOnRedditXAndTheRemoteBoards() {
		GeneratedQueries queries = this.generator.generate(JAVA_IN_EUROPE);

		assertThat(queries.get(Platform.REDDIT).query()).isEqualTo(
				"(site:reddit.com/r/forhire OR site:reddit.com/r/remotejs OR site:reddit.com/r/hiring) "
						+ "(\"Java Developer\" OR \"Backend Engineer\") remote Europe -junior -\"team lead\"");
		assertThat(queries.get(Platform.X).query()).isEqualTo("(\"hiring\" OR \"we're hiring\" OR \"now hiring\") "
				+ "(\"Java Developer\" OR \"Backend Engineer\") remote Europe -junior -\"team lead\" -filter:retweets");
		assertThat(queries.get(Platform.REMOTE_BOARDS).query()).isEqualTo(
				"(site:remoteok.com OR site:weworkremotely.com OR site:remotive.com) "
						+ "(\"Java Developer\" OR \"Backend Engineer\") (full-time OR contract) Europe -junior -\"team lead\"");
	}

	@Test
	void keepsTermsFromBreakingTheQuery() {
		SearchCriteria criteria = SearchCriteria.builder().titles(List.of("\"C++\" (embedded)", " ( ) ")).build();

		assertThat(this.generator.generate(criteria).get(Platform.LINKEDIN).query()).isEqualTo("\"C++ embedded\"");
	}

	@Test
	void hasNothingToSearchOnLinkedInWithoutTerms() {
		GeneratedQuery linkedIn = this.generator.generate(SearchCriteria.withDefaultSites()).get(Platform.LINKEDIN);

		assertThat(linkedIn.query()).isEmpty();
		assertThat(linkedIn.searchUrl()).isNull();
	}

	@Test
	void startsWithTheDefaultSites() {
		assertThat(this.generator.generate(SearchCriteria.withDefaultSites()).get(Platform.GOOGLE).query())
			.isEqualTo("(site:jobs.lever.co OR site:boards.greenhouse.io OR site:jobs.ashbyhq.com)");
	}

	@Test
	void shortensTheGoogleQueryToItsWordLimit() {
		List<String> titles = IntStream.rangeClosed(1, 20).mapToObj(n -> "Role" + n + " Engineer").toList();
		SearchCriteria criteria = SearchCriteria.builder()
			.titles(titles)
			.locations(List.of("Europe"))
			.excludes(List.of("junior"))
			.build();

		GeneratedQueries queries = this.generator.generate(criteria);

		// Each title takes two words, and each OR one more: 11 titles make the 32 words, and Europe no longer fits
		assertThat(queries.googleTruncated()).isTrue();
		assertThat(queries.googleWords()).isEqualTo(GeneratedQueries.GOOGLE_WORD_LIMIT);
		assertThat(queries.get(Platform.GOOGLE).query()).startsWith("(\"Role1 Engineer\" OR")
			.contains("\"Role11 Engineer\")")
			.doesNotContain("Role12", "Europe", "-junior");
		// The other platforms have no limit
		assertThat(queries.get(Platform.LINKEDIN).query()).contains("Role20", "Europe", "NOT junior");
	}

	@Test
	void stopsAtAGroupThatDoesNotFit() {
		List<String> titles = IntStream.rangeClosed(1, 16).mapToObj(n -> "Role" + n).toList();
		SearchCriteria criteria = SearchCriteria.builder()
			.titles(titles)
			.locations(List.of("Western Europe"))
			.excludes(List.of("junior"))
			.build();

		GeneratedQueries queries = this.generator.generate(criteria);

		// 16 titles take 31 words: the two words of the location don't fit, and the exclusion after it is left out too
		assertThat(queries.googleWords()).isEqualTo(31);
		assertThat(queries.googleTruncated()).isTrue();
		assertThat(queries.get(Platform.GOOGLE).query()).endsWith("Role16)");
	}

	@Test
	void opensTheSearchesForRecentPostings() {
		SearchCriteria criteria = JAVA_IN_EUROPE.toBuilder().recency(Recency.WEEK).build();

		GeneratedQueries queries = this.generator.generate(criteria);

		assertThat(queries.get(Platform.GOOGLE).searchUrl()).startsWith("https://www.google.com/search?q=%28site%3Ajobs.lever.co")
			.endsWith("&tbs=qdr:w,sbd:1");
		assertThat(queries.get(Platform.LINKEDIN).searchUrl())
			.startsWith("https://www.linkedin.com/jobs/search/?keywords=%28%22Java+Developer%22")
			.endsWith("&f_TPR=r604800");
		assertThat(queries.get(Platform.X).searchUrl()).startsWith("https://x.com/search?q=").endsWith("&f=live");
		assertThat(queries.get(Platform.REDDIT).searchUrl()).endsWith("&tbs=qdr:w,sbd:1");
	}

	@Test
	void opensTheSearchesForAnyDateByDefault() {
		GeneratedQueries queries = this.generator.generate(JAVA_IN_EUROPE);

		assertThat(queries.get(Platform.GOOGLE).searchUrl()).doesNotContain("tbs=");
		assertThat(queries.get(Platform.LINKEDIN).searchUrl()).doesNotContain("f_TPR");
	}

}
