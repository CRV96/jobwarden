package com.jobwarden.web.view;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Where to suggest a feature, for the templates (the profile menu, each page's How it works, Settings' About): a new
 * discussion in the Ideas category of JobWarden's GitHub repository, with the feature request's two questions and the
 * version running already in it ({@code ${@ideas.suggest()}}); and the ideas suggested so far, to vote for
 * ({@code ${@ideas.all()}}). Both open on GitHub, which needs an account to post or vote: JobWarden sends nothing.
 */
@Component("ideas")
public class Ideas {

	/** The discussions' category for ideas, by its slug, as the repository's discussions name it */
	static final String CATEGORY = "ideas";

	private final URI suggest;

	private final URI all;

	/**
	 * @param repository where JobWarden's code is on GitHub, the repository its releases come from, e.g. CRV96/jobwarden
	 * @param version the version running, which the suggestion names, e.g. 0.1.0
	 */
	public Ideas(@Value("${jobwarden.updates.repository:CRV96/jobwarden}") String repository,
			@Value("${jobwarden.version:dev}") String version) {
		String discussions = "https://github.com/" + repository + "/discussions";
		this.suggest = UriComponentsBuilder.fromUriString(discussions + "/new")
			.queryParam("category", "{category}")
			.queryParam("body", "{body}")
			.encode()
			.buildAndExpand(Map.of("category", CATEGORY, "body", body(version)))
			.toUri();
		this.all = URI.create(discussions + "/categories/" + CATEGORY);
	}

	/**
	 * @return a new discussion among the ideas, its text started: the questions the issues' feature request asks, as
	 * comments GitHub doesn't show once it's posted, and the version
	 */
	public URI suggest() {
		return this.suggest;
	}

	/**
	 * @return the ideas suggested so far, to vote for or add to
	 */
	public URI all() {
		return this.all;
	}

	/** In English, like the repository's discussions, whatever language the pages are in */
	private static String body(String version) {
		return """
				### What it would help with

				<!-- The part of the job hunt that's harder than it should be today -->

				### The idea

				<!-- How JobWarden could help, and where it would show -->

				---
				JobWarden %s
				""".formatted(version);
	}

}
