package com.jobwarden.web.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CalendarMonthTests {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Test
	void showsWholeWeeksFromTheWeekOfTheFirstDayToTheWeekOfTheLast() {
		// 1 October 2026 is a Thursday, 31 October a Saturday
		assertThat(CalendarMonth.firstShown(OCTOBER, DayOfWeek.MONDAY)).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(CalendarMonth.lastShown(OCTOBER, DayOfWeek.MONDAY)).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(CalendarMonth.firstShown(OCTOBER, DayOfWeek.SUNDAY)).isEqualTo(LocalDate.of(2026, 9, 27));
		assertThat(CalendarMonth.lastShown(OCTOBER, DayOfWeek.SUNDAY)).isEqualTo(LocalDate.of(2026, 10, 31));

		CalendarMonth month = CalendarMonth.of(OCTOBER, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 27), List.of());
		assertThat(month.weeks()).hasSize(5).allSatisfy(week -> assertThat(week).hasSize(7));
		assertThat(month.weeks().getFirst().getFirst()).satisfies(day -> {
			assertThat(day.date()).isEqualTo(LocalDate.of(2026, 9, 28));
			assertThat(day.inMonth()).isFalse();
		});
	}

	@Test
	void putsEachEntryOnItsDayAndMarksToday() {
		CalendarEntry interview = entry(LocalDate.of(2026, 10, 30), "Backend engineer");
		CalendarEntry deadline = entry(LocalDate.of(2026, 11, 1), "Platform engineer");

		CalendarMonth month = CalendarMonth.of(OCTOBER, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 27),
				List.of(interview, deadline));

		List<CalendarMonth.Day> days = month.weeks().stream().flatMap(List::stream).toList();
		assertThat(days).filteredOn(CalendarMonth.Day::today).extracting(CalendarMonth.Day::date)
			.containsExactly(LocalDate.of(2026, 10, 27));
		assertThat(days).filteredOn(day -> !day.entries().isEmpty())
			.extracting(CalendarMonth.Day::date, day -> day.entries().getFirst().title())
			.containsExactly(tuple(LocalDate.of(2026, 10, 30), "Backend engineer"),
					tuple(LocalDate.of(2026, 11, 1), "Platform engineer"));
	}

	private static CalendarEntry entry(LocalDate date, String title) {
		return CalendarEntry.builder()
			.date(date)
			.kind(CalendarEntryKind.EVENT)
			.applicationId(7)
			.title(title)
			.status(ApplicationStatus.INTERVIEWING)
			.build();
	}

}
