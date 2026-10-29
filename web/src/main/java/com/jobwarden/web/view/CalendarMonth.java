package com.jobwarden.web.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.CalendarEntry;

/**
 * A month as the calendar page shows it: whole weeks, from the week of its first day to the week of its last, each
 * day with what happens on it.
 *
 * @param weeks seven days each, starting on the first day of the week
 */
public record CalendarMonth(YearMonth month, List<List<Day>> weeks) {

	/**
	 * @param inMonth whether the day is in the month shown, not in a week it shares with the month before or after
	 * @param entries in the order the service returns them: the entries without a time first
	 */
	public record Day(LocalDate date, boolean inMonth, boolean today, List<CalendarEntry> entries) {
	}

	/**
	 * @return the first day the month's grid shows: the start of the week of its first day
	 */
	public static LocalDate firstShown(YearMonth month, DayOfWeek firstDayOfWeek) {
		return month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
	}

	/**
	 * @return the last day the month's grid shows: the end of the week of its last day
	 */
	public static LocalDate lastShown(YearMonth month, DayOfWeek firstDayOfWeek) {
		return month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDayOfWeek.minus(1)));
	}

	/**
	 * @param entries what happens from {@link #firstShown} to {@link #lastShown}
	 */
	public static CalendarMonth of(YearMonth month, DayOfWeek firstDayOfWeek, LocalDate today,
			List<CalendarEntry> entries) {
		Map<LocalDate, List<CalendarEntry>> byDay = entries.stream()
			.collect(Collectors.groupingBy(CalendarEntry::date));
		List<List<Day>> weeks = new ArrayList<>();
		LocalDate last = lastShown(month, firstDayOfWeek);
		for (LocalDate start = firstShown(month, firstDayOfWeek); !start.isAfter(last); start = start.plusWeeks(1)) {
			List<Day> week = new ArrayList<>(7);
			for (LocalDate date = start; date.isBefore(start.plusWeeks(1)); date = date.plusDays(1)) {
				week.add(new Day(date, YearMonth.from(date).equals(month), date.equals(today),
						byDay.getOrDefault(date, List.of())));
			}
			weeks.add(week);
		}
		return new CalendarMonth(month, weeks);
	}

}
