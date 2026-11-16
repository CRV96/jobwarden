package com.jobwarden.jobs.dto;

import java.util.Objects;

import com.jobwarden.jobs.enums.CalendarEntryKind;
import org.jspecify.annotations.Nullable;

/**
 * The calendar entry a reminder is for: an event of a job's timeline, its deadline, or the day to follow up on it.
 *
 * @param eventId the event, for {@link CalendarEntryKind#EVENT}; {@code null} for the others
 */
public record ReminderTarget(long applicationId, CalendarEntryKind kind, @Nullable Long eventId) {

	/**
	 * An event needs its id, and only an event has one, so an impossible target fails here, right away.
	 */
	public ReminderTarget {
		Objects.requireNonNull(kind, "kind");
		if ((kind == CalendarEntryKind.EVENT) != (eventId != null)) {
			throw new IllegalArgumentException(kind + " with event " + eventId);
		}
	}

}
