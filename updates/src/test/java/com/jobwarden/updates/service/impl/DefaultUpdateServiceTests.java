package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUpdateServiceTests {

	private static final String RELEASE = """
			{"tag_name": "v0.2.0", "name": "JobWarden 0.2.0",
			 "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v0.2.0",
			 "published_at": "2026-10-27T10:00:00Z", "draft": false, "prerelease": false}""";

	private HttpServer server;

	private final List<String> asked = new ArrayList<>();

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	@Test
	void saysWhenTheLatestReleaseIsNewer() throws IOException {
		GitHubReleases github = new GitHubReleases(serve(200, RELEASE));
		DefaultUpdateService updates = new DefaultUpdateService(github, "0.1.0", "CRV96/jobwarden", true);

		updates.check();

		UpdateStatus status = updates.status();
		assertThat(status.version()).isEqualTo("0.1.0");
		assertThat(status.latest()).isEqualTo(new Release("0.2.0", "JobWarden 0.2.0",
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v0.2.0"), Instant.parse("2026-10-27T10:00:00Z")));
		assertThat(status.updateAvailable()).isTrue();
		assertThat(status.releases()).isEqualTo(URI.create("https://github.com/CRV96/jobwarden/releases"));
		assertThat(this.asked).containsExactly("/repos/CRV96/jobwarden/releases/latest JobWarden/0.1.0");
		assertThat(new DefaultUpdateService(github, "0.2.0", "CRV96/jobwarden", true)).satisfies(running -> {
			running.check();
			assertThat(running.status().updateAvailable()).isFalse();
		});
	}

	@Test
	void hasNoneBeforeTheFirstReleaseOrWhenGitHubFails() throws IOException {
		DefaultUpdateService none = new DefaultUpdateService(new GitHubReleases(serve(404, "{}")), "0.1.0", "CRV96/jobwarden", true);
		none.check();
		assertThat(none.status().latest()).isNull();
		this.server.stop(0);

		DefaultUpdateService failing = new DefaultUpdateService(new GitHubReleases(serve(500, "")), "0.1.0", "CRV96/jobwarden", true);
		failing.check();
		assertThat(failing.status().latest()).isNull();
		assertThat(failing.status().updateAvailable()).isFalse();
	}

	@Test
	void neverAsksWhenTurnedOff() throws IOException {
		DefaultUpdateService off = new DefaultUpdateService(new GitHubReleases(serve(200, RELEASE)), "0.1.0", "CRV96/jobwarden", false);

		assertThat(off.status().latest()).isNull();
		assertThat(this.asked).isEmpty();
	}

	@Test
	void leavesOutATagThatIsntAVersion() {
		assertThat(GitHubReleases.release(JsonMapper.builder().build()
			.readTree("{\"tag_name\": \"latest\", \"html_url\": \"https://github.com/x/y/releases/tag/latest\"}"))).isEmpty();
	}

	private URI serve(int status, String body) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			this.asked.add(exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("User-Agent"));
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		this.server.start();
		return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort());
	}

}
