package com.jobwarden.updates.service.impl;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Asks GitHub for the latest release as the app starts, then again when it's older than {@link #CHECK_EVERY}, in the
 * background: pages never wait for GitHub. When GitHub can't be reached, it keeps what it had and asks again sooner.
 */
@Service
@CustomLog
class DefaultUpdateService implements UpdateService {

	static final Duration CHECK_EVERY = Duration.ofHours(6);

	static final Duration RETRY_AFTER = Duration.ofHours(1);

	private final GitHubReleases github;

	private final String version;

	private final String repository;

	private final boolean enabled;

	private final AtomicReference<@Nullable Checked> checked = new AtomicReference<>();

	private final AtomicBoolean checking = new AtomicBoolean();

	/**
	 * @param version the version the build put in the app's properties
	 * @param repository where JobWarden is released, e.g. CRV96/jobwarden
	 * @param enabled false to never ask GitHub
	 */
	DefaultUpdateService(GitHubReleases github, @Value("${jobwarden.version:dev}") String version,
			@Value("${jobwarden.updates.repository:CRV96/jobwarden}") String repository,
			@Value("${jobwarden.updates.check:true}") boolean enabled) {
		this.github = github;
		this.version = version;
		this.repository = repository;
		this.enabled = enabled;
	}

	@Override
	public UpdateStatus status() {
		Checked last = this.checked.get();
		boolean due = last == null || Instant.now().isAfter(last.checkAgainAt());
		if (this.enabled && due && this.checking.compareAndSet(false, true)) {
			Thread.ofVirtual().name("update-check").start(this::check);
		}
		return new UpdateStatus(this.version, (last != null) ? last.latest() : null,
				URI.create("https://github.com/" + this.repository + "/releases"));
	}

	@EventListener(ApplicationReadyEvent.class)
	void checkAtStart() {
		status();
	}

	/**
	 * Asks GitHub now. Once at a time: {@link #status()} starts it only when no check is running.
	 */
	void check() {
		try {
			Release latest = this.github.latest(this.repository, "JobWarden/" + this.version).orElse(null);
			this.checked.set(new Checked(latest, Instant.now().plus(CHECK_EVERY)));
			log.info("Latest JobWarden release: {}; this is {}", (latest != null) ? latest.version() : "none yet",
					this.version);
		} catch (IOException | RuntimeException failed) {
			log.warn("Couldn't ask GitHub for JobWarden's latest release: {}", failed.toString());
			Checked last = this.checked.get();
			this.checked.set(new Checked((last != null) ? last.latest() : null, Instant.now().plus(RETRY_AFTER)));
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
