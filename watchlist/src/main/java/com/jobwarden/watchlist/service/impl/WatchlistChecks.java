package com.jobwarden.watchlist.service.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads the watched boards whose time has come, each every {@code jobwarden.watchlist.every} (six hours unless said
 * otherwise): it looks every ten minutes unless {@code jobwarden.watchlist.tick} says otherwise, the first time a few
 * minutes after the server starts. Restarting the server doesn't read the boards again before their time.
 * <p>
 * Reading many boards can take a minute: it happens on a thread of its own, so the other schedules (the mailboxes,
 * the reminders) don't wait for it, and once at a time.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class WatchlistChecks {

	private final DefaultWatchlistService watchlist;

	private final AtomicBoolean reading = new AtomicBoolean();

	@Scheduled(initialDelayString = "${jobwarden.watchlist.first-check:PT3M}",
			fixedDelayString = "${jobwarden.watchlist.tick:PT10M}")
	void checkDue() {
		if (this.reading.compareAndSet(false, true)) {
			Thread.ofVirtual().name("watchlist").start(() -> {
				try {
					this.watchlist.checkDue();
				} catch (RuntimeException failed) {
					log.warn("Couldn't read the watched boards: {}", failed.toString());
				} finally {
					this.reading.set(false);
				}
			});
		}
	}

}
