package com.jobwarden.search;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * How recent the postings must be. It isn't part of a query: the address that opens the search asks the site for it.
 */
public enum Recency {

	ANY(null, null), DAY("d", Duration.ofDays(1)), WEEK("w", Duration.ofDays(7)), MONTH("m", Duration.ofDays(30)),
	YEAR("y", Duration.ofDays(365));

	private final @Nullable String googlePeriod;

	private final @Nullable Duration age;

	Recency(@Nullable String googlePeriod, @Nullable Duration age) {
		this.googlePeriod = googlePeriod;
		this.age = age;
	}

	/**
	 * @return Google's name for the period, as in {@code tbs=qdr:w}; {@code null} for any time
	 */
	@Nullable String googlePeriod() {
		return this.googlePeriod;
	}

	/**
	 * @return how old a posting may be, as LinkedIn's {@code f_TPR=r<seconds>} asks; {@code null} for any age
	 */
	@Nullable Duration age() {
		return this.age;
	}

}
