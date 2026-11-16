package com.jobwarden.discover.dto;

import java.util.ArrayList;
import java.util.List;

import com.jobwarden.search.Recency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequirementsTests {

	@Test
	void leftOutTheyAskForNothingInParticular() {
		assertThat(AiRequirements.NONE.titles()).isEmpty();
		assertThat(AiRequirements.NONE.workTypes()).isEmpty();
		assertThat(AiRequirements.NONE.recency()).isEqualTo(Recency.ANY);
		assertThat(AiRequirements.NONE.maxJobs()).isEqualTo(AiRequirements.DEFAULT_MAX_JOBS);
	}

	@Test
	void askForAsManyJobsAsAllowedAtMost() {
		assertThat(AiRequirements.builder().maxJobs(500).build().maxJobs()).isEqualTo(AiRequirements.MAX_JOBS);
		assertThat(AiRequirements.builder().maxJobs(-3).build().maxJobs()).isEqualTo(AiRequirements.DEFAULT_MAX_JOBS);
		assertThat(AiRequirements.builder().maxJobs(20).build().maxJobs()).isEqualTo(20);
	}

	@Test
	void keepTheirListsAsTheyWereWhenMade() {
		List<String> titles = new ArrayList<>(List.of("Java Developer"));
		AiRequirements requirements = AiRequirements.builder().titles(titles).build();
		titles.add("Backend Engineer");

		assertThat(requirements.titles()).containsExactly("Java Developer");
	}

}
