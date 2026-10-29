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
			.contains("name=\"sites\" value=\"jobs.lever.co\"", "name=\"platform\" value=\"GOOGLE\"")
			// They're in the Google query once there's something to search for
			.contains("Your queries show here");
		assertThat(this.mvc.get().uri("/search?titles=Java Developer")).bodyText()
			.contains("<span class=\"q-paren\">(</span><span class=\"q-field\">site:jobs.lever.co</span>",
					"<span class=\"q-operator\">OR</span>", "<span class=\"q-phrase\">&quot;Java Developer&quot;</span>",
					" / 32 words")
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
			.contains("value=\"Cluj-Napoca, Romania\"", "<span class=\"q-phrase\">&quot;Cluj-Napoca, Romania&quot;</span>",
					"<span class=\"q-operator\">NOT</span>", "<span class=\"q-phrase\">&quot;team lead&quot;</span>",
					"f_TPR=r604800", "name=\"platform\" value=\"LINKEDIN\"")
			// Every platform's query at once
			.contains("id=\"query-GOOGLE\"", "id=\"query-LINKEDIN\"", "id=\"query-REDDIT\"", "id=\"query-X\"",
					"id=\"query-REMOTE_BOARDS\"")
			.doesNotContain("Your queries show here");
	}

	@Test
	void keepsNoSitesOnceTheyAreRemoved() {
		// The page always sends the platform: without sites, the Google query has none
		assertThat(this.mvc.get().uri("/search?titles=QA Engineer&platform=GOOGLE")).bodyText()
			.contains("<pre class=\"query-box min-h-0\"><span class=\"q-phrase\">&quot;QA Engineer&quot;</span></pre>")
			.doesNotContain("site:jobs.lever.co");
	}

	@Test
	void ignoresValuesItDoesNotKnow() {
		assertThat(this.mvc.get().uri("/search?workTypes=ON_MARS&recency=FOREVER&platform=MYSPACE")).hasStatusOk()
			.bodyText()
			.contains("name=\"platform\" value=\"GOOGLE\"", "name=\"recency\" value=\"ANY\" checked");
	}

	@Test
	void saysWhereTheQueriesGoWhenThereAreNone() {
		assertThat(this.mvc.get().uri("/search?locations=Europe&workTypes=REMOTE&platform=GOOGLE")).bodyText()
			.contains("Your queries show here")
			.doesNotContain("id=\"query-GOOGLE\"");
	}

	@Test
	void answersHtmxWithTheQueriesOnly() {
		assertThat(this.mvc.get().uri("/search?titles=Data Engineer&platform=X").header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.startsWith("<div id=\"search-output\"")
			.contains("<span class=\"q-phrase\">&quot;Data Engineer&quot;</span>", "<span class=\"q-exclude\">-filter:retweets</span>",
					"https://x.com/search?q=")
			.doesNotContain("<html", "data-tag-field");
	}

	@Test
	void fillsTheFiltersFromADescriptionKeepingTheSites() {
		assertThat(this.mvc.get()
			.uri("/search?describe=senior backend engineer, remote Europe, not junior&sites=apply.workable.com&platform=GOOGLE")
			.header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			// The whole page: HTMX swaps the filters, and the queries out of band
			.contains("id=\"boolean-search\"", "id=\"search-output\"", "value=\"Backend Engineer\"", "value=\"Europe\"",
					"name=\"workTypes\" value=\"REMOTE\" checked", "value=\"junior\"",
					"<span class=\"q-field\">site:apply.workable.com</span>", "<span class=\"q-exclude\">-junior</span>")
			.doesNotContain("site:jobs.lever.co");
	}

	@Test
	void speaksTheRequestsLanguage() {
		assertThat(this.mvc.get().uri("/search?titles=Java Developer").header(HttpHeaders.ACCEPT_LANGUAGE, "ro")).bodyText()
			.contains("Constructor de căutări", "Cuvinte: ", "Site-uri de joburi remote");
	}

}
