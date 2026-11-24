package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
			[{"tag_name": "v0.2.0", "name": "JobWarden 0.2.0",
			 "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v0.2.0",
			 "published_at": "2026-10-27T10:00:00Z", "draft": false, "prerelease": false}]""";

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
		GitHubReleases github = new GitHubReleases(githubAt(serve(200, RELEASE)));
		DefaultUpdateService updates = new DefaultUpdateService(github, "0.1.0", checking(true), Clock.systemUTC());

		updates.check();

		UpdateStatus status = updates.status();
		assertThat(status.version()).isEqualTo("0.1.0");
		assertThat(status.latest()).isEqualTo(new Release("0.2.0", "JobWarden 0.2.0",
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v0.2.0"), Instant.parse("2026-10-27T10:00:00Z")));
		assertThat(status.updateAvailable()).isTrue();
		assertThat(status.releases()).isEqualTo(URI.create("https://github.com/CRV96/jobwarden/releases"));
		assertThat(this.asked).containsExactly("/repos/CRV96/jobwarden/releases?per_page=30 JobWarden/0.1.0");
		assertThat(new DefaultUpdateService(github, "0.2.0", checking(true), Clock.systemUTC())).satisfies(running -> {
			running.check();
			assertThat(running.status().updateAvailable()).isFalse();
		});
	}

	@Test
	void hasNoneBeforeTheFirstReleaseOrWhenGitHubFails() throws IOException {
		DefaultUpdateService none = new DefaultUpdateService(new GitHubReleases(githubAt(serve(404, "{}"))), "0.1.0", checking(true), Clock.systemUTC());
		none.check();
		assertThat(none.status().latest()).isNull();
		this.server.stop(0);

		DefaultUpdateService failing = new DefaultUpdateService(new GitHubReleases(githubAt(serve(500, ""))), "0.1.0", checking(true), Clock.systemUTC());
		failing.check();
		assertThat(failing.status().latest()).isNull();
		assertThat(failing.status().updateAvailable()).isFalse();
	}

	@Test
	void neverAsksWhenTurnedOff() throws IOException {
		DefaultUpdateService off = new DefaultUpdateService(new GitHubReleases(githubAt(serve(200, RELEASE))), "0.1.0", checking(false), Clock.systemUTC());

		assertThat(off.status().latest()).isNull();
		assertThat(this.asked).isEmpty();
	}

	/** On its schedule: once a day, whether or not a page asks. */
	@Test
	void asksOnceADayWhetherOrNotAPageAsks() throws IOException {
		MovingClock clock = new MovingClock();
		URI api = serve(200, RELEASE);
		DefaultUpdateService updates = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "0.1.0", githubAt(api),
				clock);

		updates.checkIfDue();
		assertThat(this.asked).hasSize(1);
		assertThat(updates.status().updateAvailable()).isTrue();

		clock.advance(Duration.ofHours(23));
		updates.checkIfDue();
		updates.status();
		assertThat(this.asked).as("not before a day, nor because a page asked").hasSize(1);

		clock.advance(Duration.ofHours(1));
		updates.checkIfDue();
		assertThat(this.asked).hasSize(2);
	}

	@Test
	void asksAgainAnHourAfterAFailedCheck() throws IOException {
		MovingClock clock = new MovingClock();
		URI api = serve(500, "");
		DefaultUpdateService updates = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "0.1.0", githubAt(api),
				clock);

		updates.checkIfDue();
		clock.advance(Duration.ofMinutes(59));
		updates.checkIfDue();
		assertThat(this.asked).hasSize(1);

		clock.advance(Duration.ofMinutes(1));
		updates.checkIfDue();
		assertThat(this.asked).hasSize(2);
	}

	@Test
	void neverAsksOnItsScheduleWhenTurnedOff() throws IOException {
		URI api = serve(200, RELEASE);
		DefaultUpdateService off = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "0.1.0",
				new UpdatesProperties("CRV96/jobwarden", false, Duration.ofDays(1), api), new MovingClock());

		off.checkIfDue();
		off.checkAtStart();

		assertThat(this.asked).isEmpty();
	}

	/** As GitHub lists them, as they were made: a draft, a pre-release, a fix to an older line, one with no notes. */
	private static final String RELEASES = """
			[{"tag_name": "v1.0.0", "name": "JobWarden 1.0.0", "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v1.0.0",
			  "published_at": "2026-11-18T17:40:33Z", "draft": false, "prerelease": false, "body_html": "<p>The first one.</p>"},
			 {"tag_name": "v1.1.0-rc1", "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v1.1.0-rc1",
			  "draft": false, "prerelease": true, "body_html": "<p>A try.</p>"},
			 {"tag_name": "v1.2.0", "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v1.2.0",
			  "draft": true, "prerelease": false, "body_html": "<p>Not out.</p>"},
			 {"tag_name": "v1.0.1", "name": "", "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v1.0.1",
			  "published_at": "2026-11-18T18:06:21Z", "draft": false, "prerelease": false,
			  "body_html": "<h2 dir=\\"auto\\">Fixed</h2><p onclick=\\"steal()\\">Runs on <strong>ARM</strong><img src=\\"https://example.com/a.png\\"><script>steal()</script></p><p><a class=\\"issue-link\\" data-id=\\"6\\" href=\\"/CRV96/jobwarden/pull/62\\">#62</a> <a href=\\"javascript:steal()\\">here</a></p>"},
			 {"tag_name": "v0.9.0", "html_url": "https://github.com/CRV96/jobwarden/releases/tag/v0.9.0",
			  "draft": false, "prerelease": false, "body_html": "  "}]""";

	@Test
	void listsTheReleasesNewestFirstWithoutDraftsOrPreReleases() throws IOException {
		DefaultUpdateService updates = new DefaultUpdateService(new GitHubReleases(githubAt(serve(200, RELEASES))), "1.0.0",
				checking(true), Clock.systemUTC());

		updates.check();

		assertThat(updates.releases()).extracting(Release::version).containsExactly("1.0.1", "1.0.0", "0.9.0");
		assertThat(updates.releases().get(0).name()).as("a blank name is none").isNull();
		assertThat(updates.releases().get(2).notes()).as("blank notes are none").isNull();
		assertThat(updates.status().latest()).isEqualTo(updates.releases().getFirst());
		assertThat(updates.status().updateAvailable()).isTrue();
		assertThat(this.asked).containsExactly("/repos/CRV96/jobwarden/releases?per_page=30 JobWarden/1.0.0");
	}

	/** The page shows the notes as they are: only text formatting is left, and links open apart, to the web. */
	@Test
	void cleansTheNotesDownToTextFormatting() throws IOException {
		DefaultUpdateService updates = new DefaultUpdateService(new GitHubReleases(githubAt(serve(200, RELEASES))), "1.0.1",
				checking(true), Clock.systemUTC());
		updates.check();

		String notes = updates.releases().getFirst().notes();

		assertThat(notes).contains("<h2>Fixed</h2>", "Runs on <strong>ARM</strong>",
				"href=\"https://github.com/CRV96/jobwarden/pull/62\"", "target=\"_blank\"", "rel=\"noopener noreferrer\"",
				">here</a>")
			.doesNotContain("<script", "steal()", "onclick", "<img", "class=", "data-id", "dir=", "javascript:");
		assertThat(updates.releases().get(1).notes()).isEqualTo("<p>The first one.</p>");
	}

	@Test
	void tellsWhatsNewSinceTheVersionSeenUpToTheOneRunning() throws IOException {
		URI api = serve(200, RELEASES);
		DefaultUpdateService updates = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "1.0.1", checking(true),
				Clock.systemUTC());
		updates.check();

		assertThat(updates.running()).map(Release::version).hasValue("1.0.1");
		assertThat(updates.whatsNew("0.9.0")).extracting(Release::version).containsExactly("1.0.1", "1.0.0");
		assertThat(updates.whatsNew("1.0.0")).extracting(Release::version).containsExactly("1.0.1");
		assertThat(updates.whatsNew(null)).as("none seen: the one running alone").extracting(Release::version)
			.containsExactly("1.0.1");
		assertThat(updates.whatsNew("not a version")).extracting(Release::version).containsExactly("1.0.1");
		assertThat(updates.whatsNew("1.0.1")).as("seen already").isEmpty();
		assertThat(updates.whatsNew("2.0.0")).as("went back to an older one").isEmpty();

		DefaultUpdateService older = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "1.0.0", checking(true),
				Clock.systemUTC());
		older.check();
		assertThat(older.whatsNew("0.1.0")).as("not past the one running").extracting(Release::version)
			.containsExactly("1.0.0", "0.9.0");
	}

	@Test
	void hasNothingNewForABuildGitHubDoesntList() throws IOException {
		URI api = serve(200, RELEASES);
		DefaultUpdateService dev = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "dev", checking(true),
				Clock.systemUTC());
		dev.check();
		DefaultUpdateService own = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "1.0.2-SNAPSHOT",
				checking(true), Clock.systemUTC());
		own.check();
		DefaultUpdateService notAsked = new DefaultUpdateService(new GitHubReleases(githubAt(api)), "1.0.1",
				checking(true), Clock.systemUTC());

		assertThat(dev.running()).isEmpty();
		assertThat(dev.whatsNew(null)).isEmpty();
		assertThat(own.whatsNew("1.0.0")).isEmpty();
		assertThat(notAsked.releases()).isEmpty();
		assertThat(notAsked.whatsNew("1.0.0")).isEmpty();
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

	private static UpdatesProperties githubAt(URI api) {
		return new UpdatesProperties("CRV96/jobwarden", true, Duration.ofDays(1), api);
	}

	private static UpdatesProperties checking(boolean check) {
		return new UpdatesProperties("CRV96/jobwarden", check, Duration.ofDays(1), URI.create("https://api.github.com"));
	}

	/** A clock the test moves on. */
	private static final class MovingClock extends Clock {

		private Instant now = Instant.parse("2026-11-16T09:00:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
