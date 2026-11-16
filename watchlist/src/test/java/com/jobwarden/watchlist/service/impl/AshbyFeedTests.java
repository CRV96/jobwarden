package com.jobwarden.watchlist.service.impl;

import com.jobwarden.search.WorkType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class AshbyFeedTests {

	private final AshbyFeed feed = new AshbyFeed();

	@Test
	void readsTheCountriesOfEveryLocationInEachOfTheirShapes() {
		// The job's own address has postalAddress; a secondary location's address is the address itself
		Posting posting = posting("""
				{"address": {"postalAddress": {"addressCountry": "USA"}},
				 "secondaryLocations": [{"location": "London", "address": {"addressCountry": "United Kingdom"}},
				                        {"location": "Berlin", "address": {"postalAddress": {"addressCountry": "Germany"}}}]}""");

		assertThat(posting.countries()).containsExactlyInAnyOrder("US", "GB", "DE");
	}

	@Test
	void goesByTheWorkplaceTypeAsIsRemoteIsTrueForAHybridJobToo() {
		assertThat(posting("{\"isRemote\": true, \"workplaceType\": \"Hybrid\"}").workType()).isEqualTo(WorkType.HYBRID);
		assertThat(posting("{\"isRemote\": false, \"workplaceType\": \"OnSite\"}").workType()).isEqualTo(WorkType.ONSITE);
		assertThat(posting("{\"isRemote\": true, \"workplaceType\": \"Remote\"}").workType()).isEqualTo(WorkType.REMOTE);
	}

	@Test
	void goesByIsRemoteOnlyWithoutAWorkplaceType() {
		assertThat(posting("{\"isRemote\": true}").workType()).isEqualTo(WorkType.REMOTE);
		assertThat(posting("{\"isRemote\": false}").workType()).isNull();
	}

	/** A listed job with a title and a link, and the fields given */
	private Posting posting(String fields) {
		JsonNode job = JsonMapper.builder().build().readTree(fields);
		((ObjectNode) job).put("title", "Platform Engineer")
			.put("jobUrl", "https://jobs.ashbyhq.com/acme/a1")
			.put("isListed", true);
		Posting posting = this.feed.posting(job);
		assertThat(posting).isNotNull();
		return posting;
	}

}
