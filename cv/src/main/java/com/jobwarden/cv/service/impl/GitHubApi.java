package com.jobwarden.cv.service.impl;

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
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.GitHubRepository;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitHub's REST API, read-only: who a user or a token is, a user's repositories, and one repository's details,
 * languages and README. With a token, what the token was given; without, what's public. Without a token GitHub answers
 * 60 requests an hour from one address, plenty for a CV.
 */
@Component
class GitHubApi {

	/** Where a repository is, before its owner/name */
	private static final String REPOS = "/repos/";

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	/** The most repositories listed: a CV shows a few. */
	private static final int PER_PAGE = 100;

	/** How much of a README Claude reads: what the project is, and how it's built, come first. */
	static final int README_MAX_LENGTH = 8000;

	private final URI api;

	private final JsonMapper json = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	GitHubApi(@Value("${jobwarden.cv.github-api:https://api.github.com}") URI api) {
		this.api = api;
	}

	/**
	 * @return the user's login as GitHub spells it, or empty when there's no such user
	 * @throws GitHubFailure when GitHub can't be reached, or answers with an error
	 */
	Optional<String> user(String username) {
		Answer answer = get("/users/" + username, null, false);
		return (answer.status() == 404) ? Optional.empty() : Optional.of(answer.json().path("login").asString(username));
	}

	/**
	 * @return the login of the token's owner, or empty when GitHub refuses the token
	 * @throws GitHubFailure when GitHub can't be reached, or answers with another error
	 */
	Optional<String> tokenOwner(String token) {
		Answer answer = get("/user", token, false);
		return (answer.status() == 401) ? Optional.empty() : Optional.of(answer.json().path("login").asString(""));
	}

	/**
	 * @param token what reads the private ones it was given; {@code null} for the public ones alone
	 * @return the repositories the user owns, or works on with a token, the last pushed to first
	 */
	List<GitHubRepository> repositories(String username, @Nullable String token) {
		String path = (token != null) ? "/user/repos?affiliation=owner,collaborator&sort=pushed&per_page=" + PER_PAGE
				: "/users/" + username + "/repos?type=owner&sort=pushed&per_page=" + PER_PAGE;
		List<GitHubRepository> repositories = new ArrayList<>();
		for (JsonNode repository : get(path, token, true).json()) {
			repositories.add(new GitHubRepository(repository.path("full_name").asString(),
					repository.path("name").asString(), text(repository, "description"), text(repository, "language"),
					repository.path("stargazers_count").asInt(0), repository.path("fork").asBoolean(false),
					repository.path("private").asBoolean(false), instant(text(repository, "pushed_at")),
					repository.path("html_url").asString()));
		}
		return repositories;
	}

	/**
	 * @param fullName owner/name, as the profile chose it from the list
	 * @return its details, languages and the start of its README; empty when it's gone, or the token can't read it
	 */
	Optional<GitHubProject> project(String fullName, @Nullable String token) {
		Answer repository = get(REPOS + fullName, token, false);
		if (repository.status() == 404) {
			return Optional.empty();
		}
		JsonNode details = repository.json();
		List<String> languages = new ArrayList<>();
		get(REPOS + fullName + "/languages", token, true).json()
			.properties()
			.stream()
			.sorted(Comparator.comparingLong((Map.Entry<String, JsonNode> language) -> language.getValue().asLong()).reversed())
			.forEach(language -> languages.add(language.getKey()));
		List<String> topics = new ArrayList<>();
		details.path("topics").forEach(topic -> topics.add(topic.asString()));
		return Optional.of(new GitHubProject(details.path("full_name").asString(fullName), details.path("name").asString(),
				details.path("html_url").asString(), text(details, "homepage"), text(details, "description"), languages,
				topics, details.path("stargazers_count").asInt(0), details.path("fork").asBoolean(false),
				instant(text(details, "created_at")), instant(text(details, "pushed_at")), readme(fullName, token)));
	}

	private @Nullable String readme(String fullName, @Nullable String token) {
		HttpResponse<String> response = send(REPOS + fullName + "/readme", token, "application/vnd.github.raw+json");
		if (response.statusCode() == 404) {
			return null;
		}
		if (response.statusCode() != 200) {
			throw new GitHubFailure("GitHub answered " + response.statusCode());
		}
		String readme = response.body().strip();
		return (readme.length() <= README_MAX_LENGTH) ? readme : readme.substring(0, README_MAX_LENGTH) + "…";
	}

	/**
	 * @param required whether a 404 is an error too: a list there must be
	 */
	private Answer get(String path, @Nullable String token, boolean required) {
		HttpResponse<String> response = send(path, token, "application/vnd.github+json");
		int status = response.statusCode();
		if ((status == 404 && !required) || status == 401) {
			return new Answer(status, this.json.createObjectNode());
		}
		if (status != 200) {
			throw new GitHubFailure("GitHub answered " + status + rateLimit(response));
		}
		try {
			return new Answer(status, this.json.readTree(response.body()));
		} catch (JacksonException notJson) {
			throw new GitHubFailure("GitHub's answer isn't JSON");
		}
	}

	private HttpResponse<String> send(String path, @Nullable String token, String accept) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(this.api + path))
			.timeout(TIMEOUT)
			.header("Accept", accept)
			.header("X-GitHub-Api-Version", "2022-11-28")
			// GitHub's API refuses requests without one
			.header("User-Agent", "JobWarden")
			.GET();
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		try {
			return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IOException failed) {
			throw new GitHubFailure("GitHub couldn't be reached: " + failed.getMessage());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new GitHubFailure("Interrupted while asking GitHub");
		}
	}

	/** When GitHub turns requests down for there being too many, until when. */
	private static String rateLimit(HttpResponse<String> response) {
		return response.headers()
			.firstValue("x-ratelimit-remaining")
			.filter("0"::equals)
			.flatMap(none -> response.headers().firstValue("x-ratelimit-reset"))
			.map(reset -> ": too many requests, until " + Instant.ofEpochSecond(Long.parseLong(reset)))
			.orElse("");
	}

	private static @Nullable String text(JsonNode node, String field) {
		String text = node.path(field).asString(null);
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static @Nullable Instant instant(@Nullable String text) {
		try {
			return (text != null) ? Instant.parse(text) : null;
		} catch (DateTimeParseException notADate) {
			return null;
		}
	}

	private record Answer(int status, JsonNode json) {
	}

	/**
	 * GitHub couldn't be reached, or answered with an error.
	 */
	static class GitHubFailure extends RuntimeException {

		GitHubFailure(String message) {
			super(message);
		}

	}

}
