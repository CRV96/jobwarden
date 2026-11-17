package com.jobwarden.web.view;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdeasTests {

	private final Ideas ideas = new Ideas("CRV96/jobwarden", "0.2.0");

	@Test
	void startsANewIdeaWithTheFeatureRequestsQuestionsAndTheVersion() {
		URI suggest = this.ideas.suggest();

		assertThat(suggest).hasScheme("https").hasHost("github.com").hasPath("/CRV96/jobwarden/discussions/new");
		assertThat(suggest.getRawQuery()).startsWith("category=ideas&body=")
			// Every character that means something in an address is escaped: the headings' #, the spaces, the comments
			.doesNotContain(" ", "#", "<", "\n");
		String body = URLDecoder.decode(suggest.getRawQuery().substring("category=ideas&body=".length()),
				StandardCharsets.UTF_8);
		assertThat(body).contains("### What it would help with", "### The idea",
				"<!-- How JobWarden could help, and where it would show -->", "JobWarden 0.2.0");
	}

	@Test
	void listsTheIdeasSuggestedSoFar() {
		assertThat(this.ideas.all()).hasToString("https://github.com/CRV96/jobwarden/discussions/categories/ideas");
	}

	@Test
	void namesAnotherRepositoryWhereItsReleasedFrom() {
		assertThat(new Ideas("someone/jobwarden-fork", "dev").suggest()).hasPath("/someone/jobwarden-fork/discussions/new");
	}

}
