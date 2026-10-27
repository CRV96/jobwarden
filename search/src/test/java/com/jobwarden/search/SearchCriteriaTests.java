package com.jobwarden.search;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCriteriaTests {

	@Test
	void keepsEachTermOnceWithoutBlanks() {
		SearchCriteria criteria = SearchCriteria.builder()
			.titles(Arrays.asList(" Java Developer ", "java developer", "", null, "Kotlin Developer"))
			.build();

		assertThat(criteria.titles()).containsExactly("Java Developer", "Kotlin Developer");
	}

	@Test
	void ordersTheChoicesAsTheirConstants() {
		SearchCriteria criteria = SearchCriteria.builder()
			.workTypes(Set.of(WorkType.ONSITE, WorkType.REMOTE))
			.build();

		assertThat(criteria.workTypes()).containsExactly(WorkType.REMOTE, WorkType.ONSITE);
	}

	@Test
	void searchesAnyTimeAndNothingByDefault() {
		SearchCriteria criteria = SearchCriteria.builder().build();

		assertThat(criteria.recency()).isEqualTo(Recency.ANY);
		assertThat(List.of(criteria.titles(), criteria.sites(), criteria.excludes())).allMatch(List::isEmpty);
		assertThat(criteria.employmentTypes()).isEmpty();
	}

}
