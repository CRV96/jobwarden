package com.jobwarden.jobs.service.impl;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.entity.ReminderEntity;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.repository.CalendarEvent;
import com.jobwarden.jobs.repository.FollowUpDates;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Calendar entries from what the repositories select, for the calendar and for the reminders.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CalendarEntries {

	/** By day, and within a day the entries without a time first, like on a timeline. */
	static final Comparator<CalendarEntry> ORDER = Comparator.comparing(CalendarEntry::date)
		.thenComparing(CalendarEntry::time, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(CalendarEntry::title);

	static CalendarEntry of(CalendarEvent event, Map<ReminderTarget, Set<ReminderLead>> reminders) {
		ReminderTarget target = new ReminderTarget(event.applicationId(), CalendarEntryKind.EVENT, event.eventId());
		return CalendarEntry.builder()
			.date(event.date())
			.time(event.time())
			.kind(CalendarEntryKind.EVENT)
			.eventId(event.eventId())
			.applicationId(event.applicationId())
			.title(event.title())
			.company(event.company())
			.status(event.status())
			.stageLabel(event.stageLabel())
			.interviewFormat(event.interviewFormat())
			.interviewLocation(event.interviewLocation())
			.durationMinutes(event.durationMinutes())
			.reminders(reminders.get(target))
			.build();
	}

	/**
	 * @param date the deadline or the day to follow up, when the application has one
	 */
	static void add(List<CalendarEntry> entries, FollowUpDates dates, LocalDate date, CalendarEntryKind kind,
			Map<ReminderTarget, Set<ReminderLead>> reminders) {
		entries.add(CalendarEntry.builder()
			.date(date)
			.kind(kind)
			.applicationId(dates.applicationId())
			.title(dates.title())
			.company(dates.company())
			.status(dates.status())
			.reminders(reminders.get(new ReminderTarget(dates.applicationId(), kind, null)))
			.build());
	}

	/**
	 * @return the reminders by the entry they're for
	 */
	static Map<ReminderTarget, Set<ReminderLead>> byTarget(List<ReminderEntity> reminders) {
		Map<ReminderTarget, Set<ReminderLead>> byTarget = new HashMap<>();
		for (ReminderEntity reminder : reminders) {
			byTarget.computeIfAbsent(target(reminder), target -> EnumSet.noneOf(ReminderLead.class))
				.add(reminder.getRemindBefore());
		}
		return byTarget;
	}

	static ReminderTarget target(ReminderEntity reminder) {
		return new ReminderTarget(reminder.getApplicationId(), reminder.getKind(), reminder.getEventId());
	}

}
