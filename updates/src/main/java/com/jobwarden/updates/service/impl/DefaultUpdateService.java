package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.jobwarden.common.logging.LogContext;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Asks GitHub for the latest release as the app starts, then on a schedule: once a day ({@code jobwarden.updates.every})
 * whether or not anyone opens a page, looking every hour ({@code jobwarden.updates.tick}) whether that's due. Pages
 * read what it last heard and never wait for GitHub; the sidebar, Settings and the extension show a newer release from
 * the next page on. When GitHub can't be reached, it keeps what it had and asks again an hour later.
 */
@Service
@CustomLog
class DefaultUpdateService implements UpdateService {

	static final Duration RETRY_AFTER = Duration.ofHours(1);

	private final GitHubReleases github;

	private final String version;

	private final String repository;

	private final boolean enabled;

	private final Duration every;

	private final Clock clock;

	private final AtomicReference<@Nullable Checked> checked = new AtomicReference<>();

	private final AtomicBoolean checking = new AtomicBoolean();

	/**
	 * @param version the version the build put in the app's properties
	 */
	DefaultUpdateService(GitHubReleases github, @Value("${jobwarden.version:dev}") String version,
			UpdatesProperties properties, Clock clock) {
		this.clock = clock;
		this.github = github;
		this.version = version;
		this.repository = properties.repository();
		this.enabled = properties.check();
		this.every = properties.every();
	}

	@Override
	public UpdateStatus status() {
		Checked last = this.checked.get();
		return new UpdateStatus(this.version, (last != null) ? last.latest() : null,
				URI.create("https://github.com/" + this.repository + "/releases"));
	}

	/** As the app starts, in the background: the start doesn't wait for GitHub. */
	@EventListener(ApplicationReadyEvent.class)
	void checkAtStart() {
		if (this.enabled && this.checking.compareAndSet(false, true)) {
			Thread.ofVirtual().name("update-check").start(LogContext.withNewId(this::check));
		}
	}

	/**
	 * Asks GitHub when the last check is a day old ({@code every}), or an hour after it failed. Restarting the server
	 * asks at once, as it starts.
	 */
	@Scheduled(initialDelayString = "${jobwarden.updates.tick:PT1H}", fixedDelayString = "${jobwarden.updates.tick:PT1H}")
	void checkIfDue() {
		Checked last = this.checked.get();
		boolean due = last == null || !this.clock.instant().isBefore(last.checkAgainAt());
		if (this.enabled && due && this.checking.compareAndSet(false, true)) {
			check();
		}
	}

	/**
	 * Asks GitHub now. Once at a time: its callers start it only when no check is running.
	 */
	void check() {
		try {
			Release latest = this.github.latest(this.repository, "JobWarden/" + this.version).orElse(null);
			this.checked.set(new Checked(latest, this.clock.instant().plus(this.every)));
			log.info("Latest JobWarden release: {}; this is {}", (latest != null) ? latest.version() : "none yet",
					this.version);
		} catch (IOException | RuntimeException failed) {
			log.warn("Couldn't ask GitHub for JobWarden's latest release: {}", failed.toString());
			Checked last = this.checked.get();
			this.checked.set(new Checked((last != null) ? last.latest() : null, this.clock.instant().plus(RETRY_AFTER)));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} finally {
			this.checking.set(false);
		}
	}

	/**
	 * @param latest the latest release, or {@code null} when there's none, or GitHub was never reached
	 */
	private record Checked(@Nullable Release latest, Instant checkAgainAt) {
	}

}
