package com.jobwarden.web.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.jobs.dto.CalendarEntry;
import org.jspecify.annotations.Nullable;

/**
 * Days as the calendar's week and day views show them: hour by hour. The entries with a time sit at their time, as
 * long as their interview lasts (half an hour without one); side by side when they overlap, in as many lanes as the
 * days' width has room for. What doesn't fit (e.g. many statuses changed at once) is one block: in the last lane, or
 * with a single lane, a badge on the entry's corner. The others (deadlines, days to follow up, events without a time)
 * go in the row above the hours.
 *
 * @param scrollTo the minute of the day the hours show first: an hour before the earliest entry, or 8:00 without one
 * @param now the minute of the day it is now, when today is one of the days
 */
public record CalendarDays(List<Day> days, int scrollTo, @Nullable Integer now) {

	/** How long an entry without a length lasts on the grid. */
	public static final int DEFAULT_LENGTH = 30;

	/**
	 * How many lanes overlapping entries share at most: a day of a week is too narrow to split, a day on its own wide.
	 */
	static final int WEEK_LANES = 1;

	static final int DAY_LANES = 6;

	public static final int DAY_MINUTES = 24 * 60;

	private static final int FIRST_HOUR_SHOWN = 8 * 60;

	/**
	 * @param allDay the entries without a time, in the order the service returns them
	 * @param timed the entries with one, by when they start
	 * @param more the entries with a time that the lanes had no room for, in blocks
	 */
	public record Day(LocalDate date, boolean today, List<CalendarEntry> allDay, List<Timed> timed, List<More> more) {
	}

	/**
	 * Entries that overlap more than the lanes have room for, as one block in the last lane, from the first one's start
	 * to the last one's end; with a single lane, as a badge on the entry it overlaps.
	 */
	public record More(List<CalendarEntry> entries, int start, int length, int lane, int lanes) {

		public int count() {
			return this.entries.size();
		}

		public boolean isBadge() {
			return this.lanes == 1;
		}

	}

	/**
	 * An entry at its time.
	 *
	 * @param start the minute of the day it starts
	 * @param length how many minutes it lasts, to the end of the day at most
	 * @param lane which of the lanes it's in, from 0, when entries overlap
	 * @param lanes how many lanes the entries it overlaps with share
	 */
	public record Timed(CalendarEntry entry, int start, int length, int lane, int lanes) {

		int end() {
			return this.start + this.length;
		}

		/**
		 * @return whether it lasts as long as it says, an interview with its length: the others happened at a moment,
		 * and show only when
		 */
		public boolean hasLength() {
			return this.entry.durationMinutes() != null;
		}

		/**
		 * @return when it ends, for the label
		 */
		public LocalTime endTime() {
			return (end() >= DAY_MINUTES) ? LocalTime.MAX : LocalTime.of(end() / 60, end() % 60);
		}

	}

	/**
	 * @param entries what happens on those days, in the order the service returns them
	 */
	public static CalendarDays of(LocalDate first, int count, LocalDate today, LocalTime now, List<CalendarEntry> entries) {
		Map<LocalDate, List<CalendarEntry>> byDay = entries.stream().collect(Collectors.groupingBy(CalendarEntry::date));
		List<Day> days = new ArrayList<>(count);
		int earliest = DAY_MINUTES;
		boolean hasToday = false;
		int maxLanes = (count == 1) ? DAY_LANES : WEEK_LANES;
		for (LocalDate date = first; date.isBefore(first.plusDays(count)); date = date.plusDays(1)) {
			List<CalendarEntry> ofDay = byDay.getOrDefault(date, List.of());
			List<Timed> laid = lay(ofDay.stream().filter(entry -> entry.time() != null).toList());
			for (Timed entry : laid) {
				earliest = Math.min(earliest, entry.start());
			}
			hasToday |= date.equals(today);
			List<More> more = new ArrayList<>();
			days.add(new Day(date, date.equals(today), ofDay.stream().filter(entry -> entry.time() == null).toList(),
					fit(laid, maxLanes, more), more));
		}
		int scrollTo = (earliest < DAY_MINUTES) ? Math.max(0, earliest - 60) : FIRST_HOUR_SHOWN;
		return new CalendarDays(days, scrollTo, hasToday ? now.getHour() * 60 + now.getMinute() : null);
	}

	/**
	 * Lays the entries of a day out in lanes: each goes in the first lane free at its start, and a group of entries
	 * that overlap one another shares the lanes it needed.
	 */
	static List<Timed> lay(List<CalendarEntry> entries) {
		List<Timed> placed = new ArrayList<>();
		List<CalendarEntry> byStart = entries.stream()
			.sorted(Comparator.comparing(CalendarEntry::time))
			.toList();
		List<Timed> group = new ArrayList<>();
		List<Integer> laneEnds = new ArrayList<>();
		int groupEnd = -1;
		for (CalendarEntry entry : byStart) {
			int start = entry.time().getHour() * 60 + entry.time().getMinute();
			int length = Math.min((entry.durationMinutes() != null) ? entry.durationMinutes() : DEFAULT_LENGTH,
					DAY_MINUTES - start);
			if (start >= groupEnd) {
				close(group, laneEnds.size(), placed);
				laneEnds.clear();
				groupEnd = start;
			}
			int lane = 0;
			while (lane < laneEnds.size() && laneEnds.get(lane) > start) {
				lane++;
			}
			if (lane == laneEnds.size()) {
				laneEnds.add(start + length);
			} else {
				laneEnds.set(lane, start + length);
			}
			group.add(new Timed(entry, start, length, lane, 0));
			groupEnd = Math.max(groupEnd, start + length);
		}
		close(group, laneEnds.size(), placed);
		return placed;
	}

	/**
	 * Keeps the entries of groups that need more lanes than there are in all but the last lane (in the only one, when
	 * there's one), and puts the others, each run of them that overlap, in a block in it.
	 *
	 * @param more where the blocks go
	 * @return the entries that keep their place
	 */
	static List<Timed> fit(List<Timed> laid, int maxLanes, List<More> more) {
		int keptLanes = Math.max(1, maxLanes - 1);
		List<Timed> kept = new ArrayList<>();
		List<CalendarEntry> folded = new ArrayList<>();
		int foldedStart = 0;
		int foldedEnd = -1;
		for (Timed entry : laid) {
			if (entry.lanes() <= maxLanes) {
				kept.add(entry);
				continue;
			}
			if (entry.lane() < keptLanes) {
				kept.add(new Timed(entry.entry(), entry.start(), entry.length(), entry.lane(), maxLanes));
				continue;
			}
			if (entry.start() >= foldedEnd && !folded.isEmpty()) {
				more.add(new More(List.copyOf(folded), foldedStart, foldedEnd - foldedStart, maxLanes - 1, maxLanes));
				folded.clear();
			}
			if (folded.isEmpty()) {
				foldedStart = entry.start();
				foldedEnd = entry.end();
			}
			folded.add(entry.entry());
			foldedEnd = Math.max(foldedEnd, entry.end());
		}
		if (!folded.isEmpty()) {
			more.add(new More(List.copyOf(folded), foldedStart, foldedEnd - foldedStart, maxLanes - 1, maxLanes));
		}
		return kept;
	}

	/** Gives the group's entries the number of lanes they share, and moves them to the placed ones. */
	private static void close(List<Timed> group, int lanes, List<Timed> placed) {
		for (Timed entry : group) {
			placed.add(new Timed(entry.entry(), entry.start(), entry.length(), entry.lane(), lanes));
		}
		group.clear();
	}

}
