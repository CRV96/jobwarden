package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.net.HttpClients;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.Version;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;

/**
 * A repository's releases, from GitHub's API, with what each says changed: the latest {@value #PER_PAGE}, without the
 * drafts and pre-releases. Without a token, GitHub answers 60 requests an hour from one address; JobWarden asks a few
 * times a day.
 */
@Component
class GitHubReleases {

	/** How many of the latest releases are asked for: GitHub's page of them, more than a page here shows. */
	static final int PER_PAGE = 30;

	private static final Duration TIMEOUT = Duration.ofSeconds(8);

	/** By version, the latest first: GitHub lists them as they were made, which a fix to an older line breaks. */
	private static final Comparator<Release> NEWEST_FIRST = Comparator
		.comparing((Release release) -> Version.parse(release.version()).orElseThrow())
		.reversed();

	private final URI api;

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClients.followingRedirects(TIMEOUT);

	GitHubReleases(UpdatesProperties properties) {
		this.api = properties.api();
	}

	/**
	 * @param repository e.g. CRV96/jobwarden
	 * @param userAgent GitHub's API refuses requests without one
	 * @return the releases, the newest version first; empty when the repository has none yet. A release whose tag
	 * isn't a version is left out.
	 * @throws IOException when GitHub can't be reached, or answers with an error
	 */
	List<Release> releases(String repository, String userAgent) throws IOException, InterruptedException {
		// The notes as HTML GitHub made of their Markdown (body_html), beside the Markdown
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.api + "/repos/" + repository + "/releases?per_page=" + PER_PAGE))
			.timeout(TIMEOUT)
			.header("Accept", "application/vnd.github.html+json")
			.header("X-GitHub-Api-Version", "2022-11-28")
			.header("User-Agent", userAgent)
			.GET()
			.build();
		HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			return List.of();
		}
		if (response.statusCode() != 200) {
			throw new IOException("GitHub answered " + response.statusCode());
		}
		JsonNode answer;
		try {
			answer = this.json.readTree(response.body());
		} catch (JacksonException notJson) {
			throw new IOException("GitHub's answer isn't JSON", notJson);
		}
		if (!answer.isArray()) {
			throw new IOException("GitHub's answer isn't a list of releases");
		}
		List<Release> releases = new ArrayList<>();
		for (JsonNode release : answer) {
			if (!release.path("draft").asBoolean(false) && !release.path("prerelease").asBoolean(false)) {
				release(release).ifPresent(releases::add);
			}
		}
		releases.sort(NEWEST_FIRST);
		return List.copyOf(releases);
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
				instant(answer.path("published_at").asString("")), ReleaseNotes.clean(answer.path("body_html").asString(""))));
	}

	private static @Nullable Instant instant(String text) {
		try {
			return text.isEmpty() ? null : Instant.parse(text);
		} catch (DateTimeParseException notADate) {
			return null;
		}
	}

}
