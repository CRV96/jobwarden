package com.jobwarden.web.controller;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.DescriptionParser;
import com.jobwarden.search.QueryGenerator;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The query rules are covered in the search module; these check what the page does with them.
 */
@WebMvcTest(SearchController.class)
@Import({ QueryGenerator.class, DescriptionParser.class })
class SearchControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@Test
	void startsWithTheDefaultSites() {
		assertThat(this.mvc.get().uri("/search")).hasStatusOk()
			.bodyText()
			.contains("(site:jobs.lever.co OR site:boards.greenhouse.io OR site:jobs.ashbyhq.com)", "5 / 32 words",
					"id=\"platform-GOOGLE\" value=\"GOOGLE\" checked")
			// The other job boards, to add with a click
			.contains("data-value=\"apply.workable.com\">");
	}

	@Test
	void searchesForTheFiltersInTheAddress() {
		assertThat(this.mvc.get()
			.uri("/search?titles=Java Developer&locations=Cluj-Napoca, Romania&workTypes=REMOTE&excludes=junior, team lead"
					+ "&recency=WEEK&platform=LINKEDIN"))
			.hasStatusOk()
			.bodyText()
			// A location with a comma stays one
			.contains("value=\"Cluj-Napoca, Romania\"",
					"&quot;Java Developer&quot; AND remote AND &quot;Cluj-Napoca, Romania&quot; NOT junior NOT &quot;team lead&quot;",
					"f_TPR=r604800", "id=\"platform-LINKEDIN\" value=\"LINKEDIN\" checked");
	}

	@Test
	void keepsNoSitesOnceTheyAreRemoved() {
		// The page always sends the platform: without sites, the Google query has none
		assertThat(this.mvc.get().uri("/search?titles=QA Engineer&platform=GOOGLE")).bodyText()
			.contains("<pre class=\"query-box\">&quot;QA Engineer&quot;</pre>");
	}

	@Test
	void ignoresValuesItDoesNotKnow() {
		assertThat(this.mvc.get().uri("/search?workTypes=ON_MARS&recency=FOREVER&platform=MYSPACE")).hasStatusOk()
			.bodyText()
			.contains("id=\"platform-GOOGLE\" value=\"GOOGLE\" checked", "name=\"recency\" value=\"ANY\" checked");
	}

	@Test
	void answersHtmxWithTheQueriesOnly() {
		assertThat(this.mvc.get().uri("/search?titles=Data Engineer&platform=X").header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.startsWith("<div id=\"search-output\"")
			.contains("&quot;Data Engineer&quot; -filter:retweets", "https://x.com/search?q=")
			.doesNotContain("<html", "data-tag-field");
	}

	@Test
	void fillsTheFiltersFromADescriptionKeepingTheSites() {
		assertThat(this.mvc.get()
			.uri("/search?describe=senior backend engineer, remote Europe, not junior&sites=apply.workable.com&platform=GOOGLE")
			.header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			// The whole form, for HTMX to swap
			.contains("id=\"boolean-search\"", "value=\"Backend Engineer\"", "value=\"Europe\"",
					"name=\"workTypes\" value=\"REMOTE\" checked", "value=\"junior\"",
					"site:apply.workable.com &quot;Backend Engineer&quot; senior remote Europe -junior")
			.doesNotContain("site:jobs.lever.co");
	}

	@Test
	void speaksTheRequestsLanguage() {
		assertThat(this.mvc.get().uri("/search").header(HttpHeaders.ACCEPT_LANGUAGE, "ro")).bodyText()
			.contains("Generator de căutări booleene", "Cuvinte: 5 / 32", "Site-uri de joburi remote");
	}

}
