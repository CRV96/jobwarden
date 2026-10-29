package com.jobwarden.hiring.enums;

import java.time.LocalDate;
import java.time.Period;

import org.jspecify.annotations.Nullable;

/**
 * How recent the posts the page shows are.
 */
public enum PostedWithin {

	ANY(null), DAY(Period.ofDays(1)), WEEK(Period.ofWeeks(1)), MONTH(Period.ofMonths(1));

	private final @Nullable Period period;

	PostedWithin(@Nullable Period period) {
		this.period = period;
	}

	/**
	 * @return the first day a post may be from, or {@code null} for any
	 */
	public @Nullable LocalDate since(LocalDate today) {
		return (this.period != null) ? today.minus(this.period) : null;
	}

}
