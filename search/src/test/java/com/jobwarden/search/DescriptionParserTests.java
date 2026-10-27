package com.jobwarden.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionParserTests {

	private final DescriptionParser parser = new DescriptionParser();

	@Test
	void readsTitlesLocationsAndContracts() {
		SearchCriteria criteria = this.parser.parse("Senior backend engineer, remote Europe, contract or freelance");

		assertThat(criteria.titles()).containsExactly("Backend Engineer");
		assertThat(criteria.skills()).containsExactly("senior");
		assertThat(criteria.locations()).containsExactly("Europe");
		assertThat(criteria.workTypes()).containsExactly(WorkType.REMOTE);
		assertThat(criteria.employmentTypes()).containsExactly(EmploymentType.CONTRACT, EmploymentType.FREELANCE);
		assertThat(criteria.sites()).isEmpty();
	}

	@Test
	void readsSkillsOutsideTheTitle() {
		SearchCriteria criteria = this.parser.parse("Java developer with Kafka and k8s, hybrid in Germany");

		assertThat(criteria.titles()).containsExactly("Java Developer");
		assertThat(criteria.skills()).containsExactly("Kafka", "Kubernetes");
		assertThat(criteria.workTypes()).containsExactly(WorkType.HYBRID);
		assertThat(criteria.locations()).containsExactly("Germany");
	}

	@Test
	void excludesWhatTheDescriptionRulesOut() {
		SearchCriteria criteria = this.parser.parse("data engineer, remote EMEA, full-time, not junior, no recruiters");

		assertThat(criteria.titles()).containsExactly("Data Engineer");
		assertThat(criteria.excludes()).containsExactly("junior", "recruiters");
		assertThat(criteria.skills()).doesNotContain("junior");
		assertThat(criteria.locations()).containsExactly("EMEA");
		assertThat(criteria.employmentTypes()).containsExactly(EmploymentType.FULL_TIME);
	}

	@Test
	void spellsTitlesTheWayPostingsDo() {
		assertThat(this.parser.parse("ios developer or devops engineer or qa automation engineer").titles())
			.containsExactly("QA Automation Engineer", "iOS Developer", "DevOps Engineer");
	}

	@Test
	void knowsOtherNamesForRegions() {
		assertThat(this.parser.parse("cloud architect anywhere in the USA or the United Kingdom").locations())
			.containsExactly("US", "UK", "Worldwide");
	}

	@Test
	void ignoresWordsInsideOtherWords() {
		// "go" isn't in "google", "eu" isn't in "museum", "intern" isn't in "international"
		SearchCriteria criteria = this.parser.parse("international museum guide at google");

		assertThat(criteria.skills()).isEmpty();
		assertThat(criteria.locations()).isEmpty();
		assertThat(criteria.employmentTypes()).isEmpty();
	}

	@Test
	void readsEveryExampleThePageOffers() {
		for (String example : SearchSuggestions.EXAMPLES) {
			assertThat(this.parser.parse(example).titles()).as(example).isNotEmpty();
		}
	}

	@Test
	void findsNothingInAnEmptyDescription() {
		SearchCriteria criteria = this.parser.parse("   ");

		assertThat(List.of(criteria.titles(), criteria.locations(), criteria.skills(), criteria.excludes()))
			.allMatch(List::isEmpty);
	}

}
