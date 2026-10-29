package com.jobwarden.web.enums;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * How much of the calendar the page shows at once. Its address names it in lower case: {@code ?view=week}.
 */
public enum CalendarView {

	/** Whole weeks, from the week of the month's first day to the week of its last. */
	MONTH,

	/** Monday to Sunday, hour by hour. */
	WEEK,

	/** One day, hour by hour. */
	DAY;

	/**
	 * @return the date as many months, weeks or days away
	 */
	public LocalDate step(LocalDate date, int steps) {
		return switch (this) {
			case MONTH -> date.plusMonths(steps);
			case WEEK -> date.plusWeeks(steps);
			case DAY -> date.plusDays(steps);
		};
	}

	/**
	 * @return the view the address names, in any case; empty for none, or one that isn't a view
	 */
	public static Optional<CalendarView> parse(@Nullable String name) {
		return (name == null) ? Optional.empty()
				: Arrays.stream(values()).filter(view -> view.name().equalsIgnoreCase(name.strip())).findFirst();
	}

	/** As the address writes it. */
	public String parameter() {
		return name().toLowerCase(Locale.ROOT);
	}

}
