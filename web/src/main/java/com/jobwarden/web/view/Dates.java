package com.jobwarden.web.view;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Dates as the templates show them, in the user's language: {@code ${@dates.relativeDay(instant)}}. Like every time
 * the pages show, in the server's time zone.
 */
@Component("dates")
@RequiredArgsConstructor
public class Dates {

	/** Until how many days ago a date shows as "3 days ago"; before that, as the date itself. */
	private static final int MAX_DAYS_AGO = 6;

	private final MessageSource messages;

	/**
	 * Today, yesterday, a few days ago, or the date for anything older (or in the future).
	 */
	public String relativeDay(Instant instant) {
		Locale locale = LocaleContextHolder.getLocale();
		LocalDate day = LocalDate.ofInstant(instant, ZoneId.systemDefault());
		long daysAgo = ChronoUnit.DAYS.between(day, LocalDate.now());
		if (daysAgo == 0) {
			return this.messages.getMessage("date.today", null, locale);
		}
		if (daysAgo == 1) {
			return this.messages.getMessage("date.yesterday", null, locale);
		}
		if (daysAgo > 1 && daysAgo <= MAX_DAYS_AGO) {
			return this.messages.getMessage("date.daysAgo", new Object[] { daysAgo }, locale);
		}
		return date(instant);
	}

	/**
	 * The date in the language's format.date pattern, e.g. 28 Sep 2026.
	 */
	public String date(Instant instant) {
		return date(LocalDate.ofInstant(instant, ZoneId.systemDefault()));
	}

	/**
	 * The moment in the language's format.dateTime pattern, e.g. 28 Sep 2026, 14:05.
	 */
	public String dateTime(Instant instant) {
		Locale locale = LocaleContextHolder.getLocale();
		String pattern = this.messages.getMessage("format.dateTime", null, locale);
		return DateTimeFormatter.ofPattern(pattern, locale).format(instant.atZone(ZoneId.systemDefault()));
	}

	/**
	 * The day in the language's format.date pattern, e.g. 28 Sep 2026.
	 */
	public String date(LocalDate day) {
		Locale locale = LocaleContextHolder.getLocale();
		String pattern = this.messages.getMessage("format.date", null, locale);
		return DateTimeFormatter.ofPattern(pattern, locale).format(day);
	}

}
