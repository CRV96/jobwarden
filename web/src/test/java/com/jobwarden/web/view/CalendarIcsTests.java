package com.jobwarden.web.view;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.web.enums.CalendarEntryType;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarIcsTests {

	private final CalendarIcs ics = new CalendarIcs(messages());

	@Test
	void writesTheInterviewsDeadlinesAndDaysToFollowUpWithTheirReminders() {
		String calendar = this.ics.write(List.of(
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 3))
					.time(LocalTime.of(14, 0))
					.kind(CalendarEntryKind.EVENT)
					.eventId(12L)
					.applicationId(7)
					.title("Backend engineer")
					.company("Acme")
					.status(ApplicationStatus.INTERVIEWING)
					.stageLabel("Technical interview")
					.interviewFormat(InterviewFormat.VIDEO)
					.interviewLocation("https://meet.example.com/abc")
					.durationMinutes(90)
					.reminders(Set.of(ReminderLead.MINUTES_15, ReminderLead.DAY_1))
					.build(),
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 5))
					.kind(CalendarEntryKind.DEADLINE)
					.applicationId(8)
					.title("Platform engineer")
					.status(ApplicationStatus.SAVED)
					.reminders(Set.of(ReminderLead.AT_TIME, ReminderLead.DAY_1))
					.build(),
				// A status change, which a calendar app has no use for
				CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 1))
					.kind(CalendarEntryKind.EVENT)
					.eventId(13L)
					.applicationId(9)
					.title("Data engineer")
					.status(ApplicationStatus.APPLIED)
					.build()),
				CalendarEntryType.FEED, id -> "http://localhost:8080/applications/" + id, ZoneId.of("Europe/Bucharest"),
				Locale.ENGLISH);

		assertThat(calendar).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n")
			.contains("X-WR-CALNAME:JobWarden\r\n")
			.endsWith("END:VCALENDAR\r\n")
			.contains("UID:event-12@jobwarden\r\n", "DTSTART:20261103T120000Z\r\n", "DTEND:20261103T133000Z\r\n",
					"SUMMARY:Technical interview · Backend engineer at Acme\r\n",
					"LOCATION:https://meet.example.com/abc\r\n",
					"DESCRIPTION:Video call\\nhttp://localhost:8080/applications/7\r\n", "TRIGGER:-PT15M\r\n",
					"TRIGGER:-PT1440M\r\n",
					// All day, its reminders from nine that morning
					"UID:deadline-8@jobwarden\r\n", "DTSTART;VALUE=DATE:20261105\r\n", "DTEND;VALUE=DATE:20261106\r\n",
					"SUMMARY:Applications close · Platform engineer\r\n", "TRIGGER:PT540M\r\n", "TRIGGER:-PT900M\r\n")
			.doesNotContain("Data engineer");
	}

	@Test
	void writesOnlyWhatTheFeedAsksForAndSaysSoInItsName() {
		List<CalendarEntry> entries = List.of(CalendarEntry.builder()
			.date(LocalDate.of(2026, 11, 3))
			.time(LocalTime.of(14, 0))
			.kind(CalendarEntryKind.EVENT)
			.eventId(12L)
			.applicationId(7)
			.title("Backend engineer")
			.status(ApplicationStatus.INTERVIEWING)
			.build(), CalendarEntry.builder()
				.date(LocalDate.of(2026, 11, 1))
				.kind(CalendarEntryKind.EVENT)
				.eventId(13L)
				.applicationId(9)
				.title("Data engineer")
				.status(ApplicationStatus.POSITION_CLOSED)
				.build(), CalendarEntry.builder()
					.date(LocalDate.of(2026, 11, 5))
					.kind(CalendarEntryKind.FOLLOW_UP)
					.applicationId(8)
					.title("Platform engineer")
					.status(ApplicationStatus.APPLIED)
					.build());

		String statuses = this.ics.write(entries, Set.of(CalendarEntryType.STATUS, CalendarEntryType.FOLLOW_UP),
				id -> "/applications/" + id, ZoneId.of("Europe/Bucharest"), Locale.ENGLISH);

		assertThat(statuses).contains("X-WR-CALNAME:JobWarden · Status changes\\, Follow-ups\r\n",
				"SUMMARY:Position closed · Data engineer\r\n", "SUMMARY:Follow up · Platform engineer\r\n")
			.doesNotContain("Backend engineer");
	}

	@Test
	void escapesTextAndFoldsLongLines() {
		assertThat(CalendarIcs.text("a, b; c\\d\ne")).isEqualTo("a\\, b\\; c\\\\d\\ne");
		StringBuilder folded = new StringBuilder();
		CalendarIcs.line(folded, "SUMMARY:" + "ă".repeat(60));
		assertThat(folded.toString().split("\r\n")).allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8))
			.hasSizeLessThanOrEqualTo(75));
		assertThat(folded.toString().replace("\r\n ", "")).isEqualTo("SUMMARY:" + "ă".repeat(60) + "\r\n");
	}

	private static StaticMessageSource messages() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("calendar.entryTitle", Locale.ENGLISH, "{0} at {1}");
		messages.addMessage("calendar.kind.DEADLINE", Locale.ENGLISH, "Applications close");
		messages.addMessage("calendar.kind.FOLLOW_UP", Locale.ENGLISH, "Follow up");
		messages.addMessage("calendar.feed.interview", Locale.ENGLISH, "Interview");
		messages.addMessage("interviewFormat.VIDEO", Locale.ENGLISH, "Video call");
		messages.addMessage("status.POSITION_CLOSED", Locale.ENGLISH, "Position closed");
		messages.addMessage("calendar.type.STATUS", Locale.ENGLISH, "Status changes");
		messages.addMessage("calendar.type.FOLLOW_UP", Locale.ENGLISH, "Follow-ups");
		return messages;
	}

}
