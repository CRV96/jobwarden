package com.jobwarden.web.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CalendarDaysTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

	@Test
	void putsTheEntriesWithATimeAtItAndTheOthersAbove() {
		CalendarDays week = CalendarDays.of(MONDAY, 7, MONDAY.plusDays(1), LocalTime.of(9, 30), List.of(
				entry("Deadline", MONDAY, null, null),
				entry("Interview", MONDAY, LocalTime.of(14, 0), 90),
				entry("Applied", MONDAY.plusDays(2), LocalTime.of(10, 15), null)));

		assertThat(week.days()).hasSize(7);
		assertThat(week.days().getFirst().allDay()).extracting(CalendarEntry::title).containsExactly("Deadline");
		assertThat(week.days().getFirst().timed().getFirst()).satisfies(interview -> {
			assertThat(interview.start()).isEqualTo(14 * 60);
			assertThat(interview.length()).isEqualTo(90);
			assertThat(interview.endTime()).isEqualTo(LocalTime.of(15, 30));
			assertThat(interview.hasLength()).isTrue();
		});
		// A status changed at a moment: it shows at its time, without an end
		assertThat(week.days().get(2).timed().getFirst().hasLength()).isFalse();
		// Half an hour without a length
		assertThat(week.days().get(2).timed().getFirst().length()).isEqualTo(CalendarDays.DEFAULT_LENGTH);
		// Today is Tuesday, at 9:30; the hours start an hour before the earliest entry, the one at 10:15
		assertThat(week.days()).filteredOn(CalendarDays.Day::today).extracting(CalendarDays.Day::date).containsExactly(MONDAY.plusDays(1));
		assertThat(week.now()).isEqualTo(9 * 60 + 30);
		assertThat(week.scrollTo()).isEqualTo(9 * 60 + 15);
		// Without entries, at 8:00
		assertThat(CalendarDays.of(MONDAY, 7, MONDAY, LocalTime.NOON, List.of()).scrollTo()).isEqualTo(8 * 60);
	}

	@Test
	void startsTheHoursBeforeAnEarlyEntry() {
		CalendarDays day = CalendarDays.of(MONDAY, 1, MONDAY.minusDays(1), LocalTime.NOON,
				List.of(entry("Early call", MONDAY, LocalTime.of(6, 30), null)));

		assertThat(day.scrollTo()).isEqualTo(5 * 60 + 30);
		assertThat(day.now()).isNull();
	}

	@Test
	void putsOverlappingEntriesSideBySide() {
		List<CalendarDays.Timed> laid = CalendarDays.lay(List.of(
				entry("A", MONDAY, LocalTime.of(10, 0), 60),
				entry("B", MONDAY, LocalTime.of(10, 30), 60),
				entry("C", MONDAY, LocalTime.of(11, 0), 30),
				entry("D", MONDAY, LocalTime.of(13, 0), 30)));

		// A and B overlap, C starts when A ends: three in two lanes; D is on its own
		assertThat(laid).extracting(timed -> timed.entry().title(), CalendarDays.Timed::lane, CalendarDays.Timed::lanes)
			.containsExactly(tuple("A", 0, 2), tuple("B", 1, 2), tuple("C", 0, 2), tuple("D", 0, 1));
	}

	@Test
	void foldsWhatTheLanesHaveNoRoomForIntoOneBlock() {
		// Five statuses changed within minutes of each other: in a week, the first keeps the day's width, the others are a
		// badge on it
		List<CalendarEntry> busy = List.of(entry("A", MONDAY, LocalTime.of(21, 0), null),
				entry("B", MONDAY, LocalTime.of(21, 5), null), entry("C", MONDAY, LocalTime.of(21, 10), null),
				entry("D", MONDAY, LocalTime.of(21, 15), null), entry("E", MONDAY, LocalTime.of(21, 20), null));

		CalendarDays.Day week = CalendarDays.of(MONDAY, 7, MONDAY, LocalTime.NOON, busy).days().getFirst();
		assertThat(week.timed()).extracting(timed -> timed.entry().title(), CalendarDays.Timed::lane, CalendarDays.Timed::lanes)
			.containsExactly(tuple("A", 0, 1));
		assertThat(week.more()).singleElement().satisfies(more -> {
			assertThat(more.entries()).extracting(CalendarEntry::title).containsExactly("B", "C", "D", "E");
			assertThat(more.start()).isEqualTo(21 * 60 + 5);
			assertThat(more.length()).isEqualTo(45);
			assertThat(more.isBadge()).isTrue();
		});

		// On its own, the day has room for all of them
		CalendarDays.Day day = CalendarDays.of(MONDAY, 1, MONDAY, LocalTime.NOON, busy).days().getFirst();
		assertThat(day.timed()).hasSize(5);
		assertThat(day.more()).isEmpty();
	}

	@Test
	void endsAnEntryAtMidnight() {
		CalendarDays.Timed late = CalendarDays.lay(List.of(entry("Late", MONDAY, LocalTime.of(23, 30), 120))).getFirst();

		assertThat(late.length()).isEqualTo(30);
		assertThat(late.endTime()).isEqualTo(LocalTime.MAX);
	}

	private static CalendarEntry entry(String title, LocalDate date, @Nullable LocalTime time, @Nullable Integer minutes) {
		return CalendarEntry.builder()
			.date(date)
			.time(time)
			.kind((time != null) ? CalendarEntryKind.EVENT : CalendarEntryKind.DEADLINE)
			.applicationId(7)
			.title(title)
			.status(ApplicationStatus.INTERVIEWING)
			.durationMinutes(minutes)
			.build();
	}

}
