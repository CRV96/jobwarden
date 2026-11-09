package com.jobwarden.web.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.dto.CalendarEntry;
import org.jspecify.annotations.Nullable;

/**
 * What the calendar can show, as its legend names them: the page shows the types picked, and leaves the others out.
 * Its address names them in lower case: {@code ?show=interview&show=deadline}.
 */
public enum CalendarEntryType {

	/** An interview: an event that moved the job to Interviewing, or another round of it. */
	INTERVIEW,

	/** Any other event of a timeline: a status the job got. */
	STATUS,

	/** The day applications to the posting close. */
	DEADLINE,

	/** The day to follow up on the job. */
	FOLLOW_UP;

	/** What the calendar shows until the profile picks: everything. */
	public static final Set<CalendarEntryType> ALL = Set.copyOf(EnumSet.allOf(CalendarEntryType.class));

	public static CalendarEntryType of(CalendarEntry entry) {
		return switch (entry.kind()) {
			case EVENT -> entry.isInterview() ? INTERVIEW : STATUS;
			case DEADLINE -> DEADLINE;
			case FOLLOW_UP -> FOLLOW_UP;
		};
	}

	/**
	 * @return the types the address names, in any case, leaving out those that aren't types: none when it names none
	 */
	public static Set<CalendarEntryType> parse(Collection<String> names) {
		Set<CalendarEntryType> types = EnumSet.noneOf(CalendarEntryType.class);
		names.forEach(name -> parse(name).ifPresent(types::add));
		return Set.copyOf(types);
	}

	private static Optional<CalendarEntryType> parse(@Nullable String name) {
		return (name == null) ? Optional.empty()
				: Arrays.stream(values()).filter(type -> type.parameter().equalsIgnoreCase(name.strip())).findFirst();
	}

	/** As the address writes it. */
	public String parameter() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

}
