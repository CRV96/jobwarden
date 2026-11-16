package com.jobwarden;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A clock a test sets, for what depends on the time: replace the application's Clock with it (Spring's
 * {@code @TestBean}), set it to a moment, act, move it on, and act again. Public so the module tests in sub-packages
 * can use it.
 */
public final class MutableClock extends Clock {

	private final ZoneId zone;

	private volatile Instant now;

	public MutableClock(LocalDateTime at, ZoneId zone) {
		this.zone = zone;
		this.now = at.atZone(zone).toInstant();
	}

	/** From now on, it's that moment, in the clock's time zone. */
	public void set(LocalDateTime at) {
		this.now = at.atZone(this.zone).toInstant();
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(LocalDateTime.ofInstant(this.now, zone), zone);
	}

	@Override
	public Instant instant() {
		return this.now;
	}

}
