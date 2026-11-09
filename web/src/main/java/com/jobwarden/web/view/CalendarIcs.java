package com.jobwarden.web.view;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * A calendar in iCalendar (RFC 5545), the format calendar apps subscribe to: the interviews, the deadlines and the
 * days to follow up, each with its reminders as the app's own alarms. Times are wall-clock times on the server, as
 * JobWarden keeps them, written in UTC so every app places them alike. An interview without a duration takes an hour;
 * a deadline or a day to follow up takes its whole day, and its reminders come at nine that morning, a day or a week
 * before, as JobWarden's own.
 */
@Component
@RequiredArgsConstructor
public class CalendarIcs {

	public static final String MEDIA_TYPE = "text/calendar;charset=UTF-8";

	/** How far back the feed goes, so what's just past still shows, and how far ahead. */
	public static final int DAYS_BEFORE = 90;

	public static final int DAYS_AFTER = 366;

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

	private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

	/** An interview without a duration. */
	private static final int DEFAULT_MINUTES = 60;

	/** Lines longer than this many bytes are folded, as the format asks. */
	private static final int LINE_BYTES = 75;

	private static final String CRLF = "\r\n";

	private final MessageSource messages;

	/**
	 * @param entries what's on the calendar; the status changes that aren't interviews are left out
	 * @param jobUrl the address of a job's page, from its id
	 */
	public String write(List<CalendarEntry> entries, LongFunction<String> jobUrl, ZoneId zone, Locale locale) {
		StringBuilder ics = new StringBuilder();
		line(ics, "BEGIN:VCALENDAR");
		line(ics, "VERSION:2.0");
		line(ics, "PRODID:-//JobWarden//Calendar//EN");
		line(ics, "CALSCALE:GREGORIAN");
		line(ics, "METHOD:PUBLISH");
		line(ics, "X-WR-CALNAME:JobWarden");
		// How often the apps that read these ask again
		line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
		line(ics, "X-PUBLISHED-TTL:PT1H");
		String stamp = UTC.format(Instant.now().atOffset(ZoneOffset.UTC));
		for (CalendarEntry entry : entries) {
			if (entry.kind() == CalendarEntryKind.EVENT && !entry.isInterview()) {
				continue;
			}
			event(ics, entry, jobUrl.apply(entry.applicationId()), stamp, zone, locale);
		}
		line(ics, "END:VCALENDAR");
		return ics.toString();
	}

	private void event(StringBuilder ics, CalendarEntry entry, String jobUrl, String stamp, ZoneId zone, Locale locale) {
		String summary = summary(entry, locale);
		line(ics, "BEGIN:VEVENT");
		line(ics, "UID:" + uid(entry));
		line(ics, "DTSTAMP:" + stamp);
		if (entry.time() != null) {
			LocalDateTime start = entry.date().atTime(entry.time());
			int minutes = (entry.durationMinutes() != null) ? entry.durationMinutes() : DEFAULT_MINUTES;
			line(ics, "DTSTART:" + utc(start, zone));
			line(ics, "DTEND:" + utc(start.plusMinutes(minutes), zone));
		} else {
			line(ics, "DTSTART;VALUE=DATE:" + DATE.format(entry.date()));
			line(ics, "DTEND;VALUE=DATE:" + DATE.format(entry.date().plusDays(1)));
			line(ics, "TRANSP:TRANSPARENT");
		}
		line(ics, "SUMMARY:" + text(summary));
		if (entry.interviewLocation() != null) {
			line(ics, "LOCATION:" + text(entry.interviewLocation()));
		}
		String format = (entry.interviewFormat() != null)
				? this.messages.getMessage("interviewFormat." + entry.interviewFormat(), null, locale) + "\n" : "";
		line(ics, "DESCRIPTION:" + text(format + jobUrl));
		line(ics, "URL:" + jobUrl);
		for (ReminderLead lead : entry.reminders().stream().sorted().toList()) {
			line(ics, "BEGIN:VALARM");
			line(ics, "ACTION:DISPLAY");
			line(ics, "DESCRIPTION:" + text(summary));
			line(ics, "TRIGGER:" + trigger(lead, entry.time() == null));
			line(ics, "END:VALARM");
		}
		line(ics, "END:VEVENT");
	}

	/** E.g. Technical interview · Backend engineer at Acme */
	private String summary(CalendarEntry entry, Locale locale) {
		String what = switch (entry.kind()) {
			case EVENT -> (entry.stageLabel() != null) ? entry.stageLabel()
					: this.messages.getMessage("calendar.feed.interview", null, locale);
			case DEADLINE, FOLLOW_UP -> this.messages.getMessage("calendar.kind." + entry.kind(), null, locale);
		};
		String job = (entry.company() != null)
				? this.messages.getMessage("calendar.entryTitle", new Object[] { entry.title(), entry.company() }, locale)
				: entry.title();
		return what + " · " + job;
	}

	/** The same for the same entry every time, so an app updates it rather than adding it again. */
	private static String uid(CalendarEntry entry) {
		return switch (entry.kind()) {
			case EVENT -> "event-" + entry.eventId() + "@jobwarden";
			case DEADLINE -> "deadline-" + entry.applicationId() + "@jobwarden";
			case FOLLOW_UP -> "follow-up-" + entry.applicationId() + "@jobwarden";
		};
	}

	/**
	 * From the start of the event: for one without a time, its start is midnight, and JobWarden's reminders count
	 * from nine that morning.
	 */
	static String trigger(ReminderLead lead, boolean allDay) {
		if (!allDay) {
			return (lead == ReminderLead.AT_TIME) ? "PT0M" : "-PT" + lead.minutes() + "M";
		}
		long minutes = ReminderLead.ALL_DAY_START.toSecondOfDay() / 60 - lead.minutes();
		return (minutes >= 0) ? "PT" + minutes + "M" : "-PT" + (-minutes) + "M";
	}

	private static String utc(LocalDateTime time, ZoneId zone) {
		return UTC.format(time.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
	}

	/** A text value, with what the format gives a meaning to escaped. */
	static String text(String value) {
		return value.replace("\\", "\\\\")
			.replace(";", "\\;")
			.replace(",", "\\,")
			.replace("\r\n", "\\n")
			.replace("\n", "\\n");
	}

	/**
	 * Adds a line, folded every 75 bytes as the format asks: each line after the first starts with a space. Never in
	 * the middle of a character.
	 */
	static void line(StringBuilder ics, String content) {
		int bytes = 0;
		int start = 0;
		for (int i = 0; i < content.length(); i = content.offsetByCodePoints(i, 1)) {
			int codePoint = content.codePointAt(i);
			int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
			if (bytes + size > LINE_BYTES) {
				ics.append(content, start, i).append(CRLF).append(' ');
				start = i;
				// The space starts the line
				bytes = 1;
			}
			bytes += size;
		}
		ics.append(content, start, content.length()).append(CRLF);
	}

}
