package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.Version;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A repository's latest release, from GitHub's API, which leaves drafts and pre-releases out. Without a token,
 * GitHub answers 60 requests an hour from one address; JobWarden asks a few times a day.
 */
@Component
class GitHubReleases {

	private static final Duration TIMEOUT = Duration.ofSeconds(8);

	private final URI api;

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	GitHubReleases(@Value("${jobwarden.updates.api:https://api.github.com}") URI api) {
		this.api = api;
	}

	/**
	 * @param repository e.g. CRV96/jobwarden
	 * @param userAgent GitHub's API refuses requests without one
	 * @return the latest release, or empty when the repository has none yet, or its tag isn't a version
	 * @throws IOException when GitHub can't be reached, or answers with an error
	 */
	Optional<Release> latest(String repository, String userAgent) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(this.api + "/repos/" + repository + "/releases/latest"))
			.timeout(TIMEOUT)
			.header("Accept", "application/vnd.github+json")
			.header("X-GitHub-Api-Version", "2022-11-28")
			.header("User-Agent", userAgent)
			.GET()
			.build();
		HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			return Optional.empty();
		}
		if (response.statusCode() != 200) {
			throw new IOException("GitHub answered " + response.statusCode());
		}
		try {
			return release(this.json.readTree(response.body()));
		} catch (JacksonException notJson) {
			throw new IOException("GitHub's answer isn't JSON", notJson);
		}
	}

	static Optional<Release> release(JsonNode answer) {
		String tag = answer.path("tag_name").asString("");
		String url = answer.path("html_url").asString("");
		Optional<Version> version = Version.parse(tag);
		if (version.isEmpty() || url.isEmpty()) {
			return Optional.empty();
		}
		String name = answer.path("name").asString("");
		return Optional.of(new Release(version.get().toString(), name.isBlank() ? null : name, URI.create(url),
				instant(answer.path("published_at").asString(""))));
	}

	private static @Nullable Instant instant(String text) {
		try {
			return text.isEmpty() ? null : Instant.parse(text);
		} catch (DateTimeParseException notADate) {
			return null;
		}
	}

}
