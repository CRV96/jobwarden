package com.jobwarden.cv.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jobwarden.cv.service.impl.GitHubApi.GitHubFailure;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Against a server here that answers as GitHub's API does, for one user, one token, and one repository.
 */
class GitHubApiTests {

	private static final String TOKEN = "github_pat_robert";

	private HttpServer github;

	private GitHubApi api;

	/** The Authorization header each path was asked with. */
	private final Map<String, String> asked = new ConcurrentHashMap<>();

	@BeforeEach
	void startGitHub() throws IOException {
		this.github = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.github.createContext("/", this::answer);
		this.github.start();
		this.api = new GitHubApi(URI.create("http://127.0.0.1:" + this.github.getAddress().getPort()));
	}

	@AfterEach
	void stopGitHub() {
		this.github.stop(0);
	}

	@Test
	void findsAUserAndATokensOwner() {
		assertThat(this.api.user("crv96")).contains("CRV96");
		assertThat(this.api.user("nobody")).isEmpty();
		assertThat(this.api.tokenOwner(TOKEN)).contains("CRV96");
		assertThat(this.api.tokenOwner("expired")).isEmpty();
		assertThat(this.asked).containsEntry("/user", "Bearer expired");
	}

	@Test
	void listsTheRepositoriesTheTokenReadsOrThePublicOnes() {
		assertThat(this.api.repositories("CRV96", TOKEN)).singleElement().satisfies(repository -> {
			assertThat(repository.fullName()).isEqualTo("CRV96/jobwarden");
			assertThat(repository.language()).isEqualTo("Java");
			assertThat(repository.isPrivate()).isTrue();
			assertThat(repository.description()).isNull();
			assertThat(repository.pushedAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
		});
		assertThat(this.api.repositories("CRV96", null)).isEmpty();
		assertThat(this.asked).containsKey("/users/CRV96/repos");
	}

	@Test
	void readsAProjectsLanguagesTopicsAndReadme() {
		assertThat(this.api.project("CRV96/jobwarden", TOKEN)).hasValueSatisfying(project -> {
			assertThat(project.languages()).containsExactly("Java", "HTML", "JavaScript");
			assertThat(project.topics()).containsExactly("job-search", "spring-boot");
			assertThat(project.homepage()).isEqualTo("https://jobwarden.dev");
			assertThat(project.readme()).startsWith("# JobWarden").endsWith("…")
				.hasSize(GitHubApi.README_MAX_LENGTH + 1);
		});
		assertThat(this.api.project("CRV96/gone", TOKEN)).isEmpty();
	}

	@Test
	void saysWhenGitHubTurnsItDown() {
		assertThatExceptionOfType(GitHubFailure.class).isThrownBy(() -> this.api.repositories("limited", null))
			.withMessageContaining("403")
			.withMessageContaining("too many requests");
	}

	private void answer(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		this.asked.put(path, (authorization != null) ? authorization : "");
		boolean token = ("Bearer " + TOKEN).equals(authorization);
		switch (path) {
			case "/users/crv96" -> send(exchange, 200, "{\"login\": \"CRV96\"}");
			case "/user" -> send(exchange, token ? 200 : 401, token ? "{\"login\": \"CRV96\"}" : "{\"message\": \"Bad credentials\"}");
			case "/user/repos" -> send(exchange, 200, """
					[{"full_name": "CRV96/jobwarden", "name": "jobwarden", "description": "", "language": "Java",
					  "stargazers_count": 3, "fork": false, "private": true, "pushed_at": "2026-11-03T10:00:00Z",
					  "html_url": "https://github.com/CRV96/jobwarden"}]
					""");
			case "/users/CRV96/repos" -> send(exchange, 200, "[]");
			case "/users/limited/repos" -> {
				exchange.getResponseHeaders().add("x-ratelimit-remaining", "0");
				exchange.getResponseHeaders().add("x-ratelimit-reset", "1793700000");
				send(exchange, 403, "{}");
			}
			case "/repos/CRV96/jobwarden" -> send(exchange, 200, """
					{"full_name": "CRV96/jobwarden", "name": "jobwarden", "html_url": "https://github.com/CRV96/jobwarden",
					 "homepage": "https://jobwarden.dev", "description": "A self-hosted job tracker",
					 "topics": ["job-search", "spring-boot"], "stargazers_count": 3, "fork": false,
					 "created_at": "2026-09-01T08:00:00Z", "pushed_at": "2026-11-03T10:00:00Z"}
					""");
			case "/repos/CRV96/jobwarden/languages" -> send(exchange, 200, "{\"HTML\": 900, \"Java\": 12000, \"JavaScript\": 300}");
			case "/repos/CRV96/jobwarden/readme" -> send(exchange, 200, "# JobWarden\n" + "x".repeat(GitHubApi.README_MAX_LENGTH));
			default -> send(exchange, 404, "{\"message\": \"Not Found\"}");
		}
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
