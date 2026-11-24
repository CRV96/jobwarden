package com.jobwarden.web.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(UpdatesController.class)
class UpdatesControllerTests {

	private static final Release NEWER = release("1.1.0", "2026-12-01T10:00:00Z", "<p>Calendar sync.</p>");

	private static final Release RUNNING = release("1.0.1", "2026-11-18T18:06:21Z",
			"<h2>Fixed</h2><p>Runs on <strong>ARM</strong>.</p>");

	private static final Release FIRST = release("1.0.0", "2026-11-18T17:40:33Z", null);

	/** Made before 1.0.1 came out: it ran an older one, so has an update to be told of. */
	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-11-10T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UpdateService updates;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@BeforeEach
	void runningOneZeroOne() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.updates.status())
			.willReturn(new UpdateStatus("1.0.1", NEWER, URI.create("https://github.com/CRV96/jobwarden/releases")));
		given(this.updates.releases()).willReturn(List.of(NEWER, RUNNING, FIRST));
		given(this.updates.running()).willReturn(Optional.of(RUNNING));
	}

	@Test
	void showsEachReleaseWithWhatItChangedAndWhichOneRuns() {
		assertThat(this.mvc.get().uri("/updates")).hasStatusOk()
			.bodyText()
			.contains("What’s new", "data-dismissible=\"update-1.1.0\"", "JobWarden 1.1.0 is out on GitHub.", "How to update",
					"https://github.com/CRV96/jobwarden/wiki/Updating", "All releases on GitHub",
					"https://github.com/CRV96/jobwarden/releases\"", "JobWarden 1.1.0", "Not installed yet",
					"<p>Calendar sync.</p>", "You’re running this", "<h2>Fixed</h2><p>Runs on <strong>ARM</strong>.</p>",
					"JobWarden 1.0.0", "This release says nothing more.",
					"https://github.com/CRV96/jobwarden/releases/tag/v1.0.0");
	}

	@Test
	void saysSoWhenGitHubListsNoRelease() {
		given(this.updates.releases()).willReturn(List.of());

		assertThat(this.mvc.get().uri("/updates")).hasStatusOk()
			.bodyText()
			.contains("No releases to show", "GitHub couldn’t be reached yet")
			.doesNotContain("You’re running this");
	}

	/** Every page says which release runs, for the popup; and, for a profile made since, that it has no update. */
	@Test
	void pagesSayTheReleaseRunningForThePopup() {
		assertThat(this.mvc.get().uri("/updates")).hasStatusOk()
			.bodyText()
			.contains("data-whats-new=\"/updates/whats-new\"", "data-app-version=\"1.0.1\"",
					"<a class=\"min-w-0 flex-1 hover:underline\" href=\"/updates\">", "What’s new</span>")
			.doesNotContain("data-profile-after-release");

		given(this.currentUser.get()).willReturn(Optional.of(new User(2, "Ana", Instant.parse("2026-11-20T10:00:00Z"))));
		assertThat(this.mvc.get().uri("/updates")).bodyText().contains("data-profile-after-release=\"true\"");

		given(this.updates.running()).willReturn(Optional.empty());
		assertThat(this.mvc.get().uri("/updates")).bodyText().doesNotContain("data-whats-new", "data-app-version");
	}

	@Test
	void thePopupTellsWhatChangedSinceTheVersionSeen() {
		given(this.updates.whatsNew("0.9.0")).willReturn(List.of(RUNNING, FIRST));

		assertThat(this.mvc.get().uri("/updates/whats-new").param("since", "0.9.0").header("HX-Request", "true"))
			.hasStatusOk()
			.bodyText()
			.contains("<dialog", "What’s new in JobWarden 1.0.1", "You’ve updated from 0.9.0. Here’s what changed since.",
					"<h2>Fixed</h2>", "JobWarden 1.0.0", "This release says nothing more.", "See every update", "Got it")
			.doesNotContain("<aside");
	}

	@Test
	void withoutAVersionSeenThePopupTellsOfTheReleaseRunning() {
		given(this.updates.whatsNew(null)).willReturn(List.of(RUNNING));

		assertThat(this.mvc.get().uri("/updates/whats-new").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("What’s new in JobWarden 1.0.1", "Here’s what changed in this release.", "<h2>Fixed</h2>")
			.as("one release alone has no heading of its own")
			.doesNotContain("On GitHub", "You’ve updated");
	}

	@Test
	void nothingWhenNothingIsNew() {
		given(this.updates.whatsNew("1.0.1")).willReturn(List.of());

		assertThat(this.mvc.get().uri("/updates/whats-new").param("since", "1.0.1").header("HX-Request", "true"))
			.hasStatus(204)
			.body()
			.isEmpty();
	}

	private static Release release(String version, String publishedAt, String notes) {
		return new Release(version, "JobWarden " + version,
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v" + version), Instant.parse(publishedAt), notes);
	}

}
