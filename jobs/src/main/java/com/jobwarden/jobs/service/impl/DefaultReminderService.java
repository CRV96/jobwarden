package com.jobwarden.jobs.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.ReminderNotice;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.entity.ReminderEntity;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.repository.CalendarEvent;
import com.jobwarden.jobs.repository.FollowUpDates;
import com.jobwarden.jobs.repository.ReminderRepository;
import com.jobwarden.jobs.service.ReminderService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultReminderService implements ReminderService {

	/**
	 * How long after a timed entry starts its reminder still comes: the pages and the extension ask every minute or so,
	 * and a reminder at the time would otherwise never come.
	 */
	private static final Duration LATE = Duration.ofMinutes(30);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private static final String SEPARATOR = " · ";

	private final ApplicationRepository applications;

	private final ReminderRepository reminders;

	private final MessageSource messages;

	private final Clock clock;

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	public List<CalendarEntry> findRemindable(long userId, long applicationId) {
		FollowUpDates job = followUpDates(userId, applicationId);
		LocalDate today = LocalDate.now(this.clock);
		Map<ReminderTarget, Set<ReminderLead>> set = CalendarEntries
			.byTarget(this.reminders.findByUserIdAndApplicationId(userId, applicationId));
		List<CalendarEntry> entries = new ArrayList<>();
		for (CalendarEvent event : this.applications.findCalendarEventsFrom(userId, applicationId, today)) {
			entries.add(CalendarEntries.of(event, set));
		}
		if (job.deadline() != null && !job.deadline().isBefore(today)) {
			CalendarEntries.add(entries, job, job.deadline(), CalendarEntryKind.DEADLINE, set);
		}
		if (job.followUpOn() != null && !job.followUpOn().isBefore(today)) {
			CalendarEntries.add(entries, job, job.followUpOn(), CalendarEntryKind.FOLLOW_UP, set);
		}
		entries.sort(CalendarEntries.ORDER);
		return entries;
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 * @throws BusinessRuleException if it has no such entry, or the entry has no time for the lead
	 */
	@Override
	@Transactional
	public void set(long userId, ReminderTarget target, ReminderLead lead, boolean on) {
		Optional<ReminderEntity> existing = this.reminders.findReminder(userId, target.applicationId(), target.kind(),
				target.eventId(), lead);
		if (on && existing.isEmpty()) {
			CalendarEntry entry = findEntry(userId, target)
				.orElseThrow(() -> new BusinessRuleException(JobsErrorCode.CALENDAR_ENTRY_NOT_FOUND,
						"Application " + target.applicationId() + " has no " + target.kind() + " " + target.eventId()));
			if (!entry.reminderChoices().contains(lead)) {
				throw new BusinessRuleException(JobsErrorCode.REMINDER_NOT_ALLOWED,
						"A " + lead + " reminder needs an entry with a time of day");
			}
			this.reminders.save(new ReminderEntity(userId, target.applicationId(), target.kind(), target.eventId(), lead));
			log.info("Set a {} reminder of the {} of application {}", lead, target.kind(), target.applicationId());
		} else if (!on && existing.isPresent()) {
			this.reminders.delete(existing.get());
			log.info("Removed a {} reminder of the {} of application {}", lead, target.kind(), target.applicationId());
		}
	}

	@Override
	@Transactional
	public List<ReminderNotice> claimDue(long userId, Locale locale) {
		List<ReminderEntity> all = this.reminders.findByUserId(userId);
		if (all.isEmpty()) {
			return List.of();
		}
		Map<ReminderTarget, CalendarEntry> entries = entries(userId, all);
		// An archived job's reminders wait: they come again with the job, unless their entry is over by then
		Set<Long> archived = new HashSet<>(this.applications.findArchivedIds(userId,
				all.stream().map(ReminderEntity::getApplicationId).distinct().toList()));
		LocalDateTime now = LocalDateTime.now(this.clock);
		// Several reminders of one entry may be due at once, e.g. after the computer slept through the first: they all
		// count as come, and say it once, with the one that's due last
		Map<ReminderTarget, Due> due = new LinkedHashMap<>();
		List<ReminderEntity> over = new ArrayList<>();
		for (ReminderEntity reminder : all.stream().sorted(Comparator.comparing(ReminderEntity::getId)).toList()) {
			ReminderTarget target = CalendarEntries.target(reminder);
			CalendarEntry entry = entries.get(target);
			if (entry == null || isOver(entry, now)) {
				over.add(reminder);
				continue;
			}
			if (archived.contains(reminder.getApplicationId()) || !isDue(reminder, entry, now)
					|| this.reminders.markNotified(reminder.getId(), entry.start()) == 0) {
				continue;
			}
			due.merge(target, new Due(reminder, entry),
					(one, other) -> (one.minutesBefore() <= other.minutesBefore()) ? one : other);
		}
		if (!due.isEmpty()) {
			log.info("{} reminders of profile {} came", due.size(), userId);
		}
		deleteOver(userId, over);
		return due.entrySet()
			.stream()
			.map(reminder -> notice(reminder.getValue().reminder(), reminder.getValue().entry(), now, locale))
			.toList();
	}

	@Override
	@Transactional
	public int deleteOver() {
		int deleted = 0;
		Map<Long, List<ReminderEntity>> byProfile = this.reminders.findAll()
			.stream()
			.collect(Collectors.groupingBy(ReminderEntity::getUserId));
		LocalDateTime now = LocalDateTime.now(this.clock);
		for (Map.Entry<Long, List<ReminderEntity>> profile : byProfile.entrySet()) {
			Map<ReminderTarget, CalendarEntry> entries = entries(profile.getKey(), profile.getValue());
			List<ReminderEntity> over = profile.getValue()
				.stream()
				.filter(reminder -> isOver(entries.get(CalendarEntries.target(reminder)), now))
				.toList();
			deleteOver(profile.getKey(), over);
			deleted += over.size();
		}
		return deleted;
	}

	private void deleteOver(long userId, List<ReminderEntity> over) {
		if (!over.isEmpty()) {
			this.reminders.deleteAllInBatch(over);
			log.info("Deleted {} reminders of profile {} whose entry is over or gone", over.size(), userId);
		}
	}

	/**
	 * Due from its lead before the entry's start until the entry is over. Once for each start: an entry moved since it
	 * came is due again.
	 */
	private static boolean isDue(ReminderEntity reminder, CalendarEntry entry, LocalDateTime now) {
		LocalDateTime start = entry.start();
		LocalDateTime comes = start.minusMinutes(reminder.getRemindBefore().minutes());
		return !now.isBefore(comes) && !start.equals(reminder.getNotifiedFor());
	}

	/**
	 * A little after a timed entry starts, at the end of the day for one without a time; or gone, when its event was
	 * removed or its deadline or day to follow up cleared: its reminders have nothing more to say.
	 */
	private static boolean isOver(@Nullable CalendarEntry entry, LocalDateTime now) {
		if (entry == null) {
			return true;
		}
		LocalDateTime end = (entry.time() != null) ? entry.start().plus(LATE) : entry.date().plusDays(1).atStartOfDay();
		return !now.isBefore(end);
	}

	/**
	 * The entries the reminders are for, as they are now, in two queries.
	 */
	private Map<ReminderTarget, CalendarEntry> entries(long userId, List<ReminderEntity> reminders) {
		Map<ReminderTarget, Set<ReminderLead>> none = Map.of();
		Map<ReminderTarget, CalendarEntry> entries = new HashMap<>();
		List<Long> events = reminders.stream().map(ReminderEntity::getEventId).filter(id -> id != null).toList();
		if (!events.isEmpty()) {
			for (CalendarEvent event : this.applications.findCalendarEventsById(userId, events)) {
				CalendarEntry entry = CalendarEntries.of(event, none);
				entries.put(entry.target(), entry);
			}
		}
		List<Long> jobs = reminders.stream()
			.filter(reminder -> reminder.getKind() != CalendarEntryKind.EVENT)
			.map(ReminderEntity::getApplicationId)
			.distinct()
			.toList();
		if (!jobs.isEmpty()) {
			List<CalendarEntry> dates = new ArrayList<>();
			for (FollowUpDates job : this.applications.findFollowUpDatesById(userId, jobs)) {
				if (job.deadline() != null) {
					CalendarEntries.add(dates, job, job.deadline(), CalendarEntryKind.DEADLINE, none);
				}
				if (job.followUpOn() != null) {
					CalendarEntries.add(dates, job, job.followUpOn(), CalendarEntryKind.FOLLOW_UP, none);
				}
			}
			dates.forEach(entry -> entries.put(entry.target(), entry));
		}
		return entries;
	}

	private Optional<CalendarEntry> findEntry(long userId, ReminderTarget target) {
		FollowUpDates job = followUpDates(userId, target.applicationId());
		return switch (target.kind()) {
			case EVENT -> this.applications.findCalendarEventsById(userId, List.of(target.eventId()))
				.stream()
				.filter(event -> event.applicationId() == target.applicationId())
				.findFirst()
				.map(event -> CalendarEntries.of(event, Map.of()));
			case DEADLINE -> entry(job, job.deadline(), CalendarEntryKind.DEADLINE);
			case FOLLOW_UP -> entry(job, job.followUpOn(), CalendarEntryKind.FOLLOW_UP);
		};
	}

	private static Optional<CalendarEntry> entry(FollowUpDates job, @Nullable LocalDate date, CalendarEntryKind kind) {
		if (date == null) {
			return Optional.empty();
		}
		List<CalendarEntry> entries = new ArrayList<>();
		CalendarEntries.add(entries, job, date, kind, Map.of());
		return Optional.of(entries.getFirst());
	}

	private FollowUpDates followUpDates(long userId, long applicationId) {
		return this.applications.findFollowUpDatesById(userId, List.of(applicationId))
			.stream()
			.findFirst()
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

	/**
	 * What's coming and when, e.g. Technical interview · in 15 min; the job, and how and where the interview takes
	 * place.
	 */
	private ReminderNotice notice(ReminderEntity reminder, CalendarEntry entry, LocalDateTime now, Locale locale) {
		String what = switch (entry.kind()) {
			case EVENT -> eventName(entry, locale);
			case DEADLINE, FOLLOW_UP -> message("notice." + entry.kind(), locale);
		};
		StringBuilder body = new StringBuilder((entry.company() != null)
				? message("notice.job", locale, entry.title(), entry.company()) : entry.title());
		if (entry.interviewFormat() != null) {
			body.append(SEPARATOR).append(message("notice.format." + entry.interviewFormat(), locale));
		}
		if (entry.interviewLocation() != null) {
			body.append(SEPARATOR).append(entry.interviewLocation());
		}
		return new ReminderNotice(reminder.getId(), entry.applicationId(), entry.kind(),
				message("notice.title", locale, what, when(entry, now, locale)), body.toString());
	}

	/**
	 * When the entry is, from now: in so many minutes when it's soon, else its day and its time.
	 */
	private String when(CalendarEntry entry, LocalDateTime now, Locale locale) {
		String time = (entry.time() != null) ? entry.time().format(TIME) : null;
		if (time != null) {
			// Between zoned times: an hour that's skipped or repeated as the clocks change counts as it is
			ZoneId zone = this.clock.getZone();
			long minutes = Duration.between(now.atZone(zone), entry.start().atZone(zone)).toMinutes();
			if (minutes <= 0) {
				return message("notice.when.now", locale);
			}
			if (minutes < 60) {
				return message("notice.when.minutes", locale, minutes);
			}
		}
		LocalDate today = now.toLocalDate();
		if (entry.date().equals(today)) {
			return (time == null) ? message("notice.when.today", locale) : message("notice.when.todayAt", locale, time);
		}
		if (entry.date().equals(today.plusDays(1))) {
			return (time == null) ? message("notice.when.tomorrow", locale)
					: message("notice.when.tomorrowAt", locale, time);
		}
		String day = entry.date().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
		return (time == null) ? message("notice.when.on", locale, day) : message("notice.when.onAt", locale, day, time);
	}

	/** The stage's own name, or what it is */
	private String eventName(CalendarEntry entry, Locale locale) {
		if (entry.stageLabel() != null) {
			return entry.stageLabel();
		}
		return message(entry.isInterview() ? "notice.interview" : "notice.event", locale);
	}

	private String message(String key, Locale locale, Object... arguments) {
		return this.messages.getMessage(key, arguments, locale);
	}

	/** A reminder that came, with the entry it's set on. */
	private record Due(ReminderEntity reminder, CalendarEntry entry) {

		int minutesBefore() {
			return this.reminder.getRemindBefore().minutes();
		}

	}

}
