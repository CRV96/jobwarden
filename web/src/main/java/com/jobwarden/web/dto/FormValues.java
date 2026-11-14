package com.jobwarden.web.dto;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FormValues {

	/**
	 * A date, and a time of day when it's known.
	 */
	record Moment(LocalDate date, @Nullable LocalTime time) {
	}

	/**
	 * A field the form's validation requires (e.g. {@code @NotBlank}), read once the form is valid: binding leaves any
	 * field out that the request didn't send, so the record can't say it's there, but by now it is.
	 *
	 * @throws IllegalStateException when the form wasn't validated first, which is a bug
	 */
	static <T> T validated(@Nullable T value, String field) {
		if (value == null) {
			throw new IllegalStateException(field + " is required, but the form wasn't validated");
		}
		return value;
	}

	/**
	 * Empty text fields arrive as empty strings; they're stored as no value.
	 */
	static @Nullable String blankToNull(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	/**
	 * From a form's date and time fields, whose time is optional: cleared, it's left out. Without a date (e.g. the
	 * status buttons send none) it's now, with the time, in the clock's time zone like every time the pages show.
	 */
	static Moment momentOrNow(@Nullable LocalDate date, @Nullable LocalTime time, Clock clock) {
		if (date != null) {
			return new Moment(date, time);
		}
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		return new Moment(now.toLocalDate(), now.toLocalTime());
	}

}
