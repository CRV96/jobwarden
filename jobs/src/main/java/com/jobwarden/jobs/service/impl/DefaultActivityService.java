package com.jobwarden.jobs.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.jobwarden.jobs.dto.JobActivity;
import com.jobwarden.jobs.dto.JobActivity.Day;
import com.jobwarden.jobs.dto.JobActivity.Entry;
import com.jobwarden.jobs.dto.JobActivity.FollowUpDue;
import com.jobwarden.jobs.dto.JobActivity.Tally;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ActivityService;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts in the database, day by day, and adds the days up here. A job is saved on the day it was captured, where the
 * profile is; an application is sent on the day its timeline says it was, counted once a day however many entries say
 * so. A week starts on Monday, as on the calendar.
 */
@Service
@RequiredArgsConstructor
class DefaultActivityService implements ActivityService {

	/** How many timeline entries the homepage shows. */
	static final int RECENT = 8;

	/** How many follow-ups due it lists. */
	static final int FOLLOW_UPS = 6;

	private static final String SAVED = """
			SELECT (coalesce(captured_at, created_at) AT TIME ZONE :zone)::date AS day, count(*) AS jobs
			FROM applications
			WHERE user_id = :userId AND coalesce(captured_at, created_at) >= :since
			GROUP BY 1
			""";

	private static final String APPLIED = """
			SELECT e.event_date AS day, count(DISTINCT a.id) AS jobs
			FROM application_events e
			JOIN applications a ON a.id = e.application_id
			WHERE a.user_id = :userId AND e.stage_category = 'APPLIED' AND e.event_date BETWEEN :from AND :today
			GROUP BY 1
			""";

	private static final String RECENT_ENTRIES = """
			SELECT a.id, a.title, a.company, e.stage_category, e.stage_label, e.event_date, e.created_at
			FROM application_events e
			JOIN applications a ON a.id = e.application_id
			WHERE a.user_id = :userId
			ORDER BY e.created_at DESC, e.id DESC
			LIMIT :limit
			""";

	private static final String FOLLOW_UPS_DUE = """
			SELECT id, title, company, follow_up_on
			FROM applications
			WHERE user_id = :userId AND archived_at IS NULL AND follow_up_on <= :today AND current_status NOT IN (:ended)
			ORDER BY follow_up_on, id
			LIMIT :limit
			""";

	private final JdbcClient jdbc;

	@Override
	@Transactional(readOnly = true)
	public JobActivity find(long userId, LocalDate today, ZoneId zone) {
		LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate monthStart = today.withDayOfMonth(1);
		LocalDate chartStart = today.minusDays(JobActivity.DAYS - 1);
		LocalDate from = Stream.of(weekStart.minusWeeks(1), monthStart.minusMonths(1), chartStart)
			.min(LocalDate::compareTo)
			.orElseThrow();
		Map<LocalDate, Integer> saved = counts(SAVED, userId, from, today, zone);
		Map<LocalDate, Integer> applied = counts(APPLIED, userId, from, today, zone);

		List<Day> days = new ArrayList<>();
		for (LocalDate day = chartStart; !day.isAfter(today); day = day.plusDays(1)) {
			days.add(new Day(day, saved.getOrDefault(day, 0), applied.getOrDefault(day, 0)));
		}
		return new JobActivity(days, tally(saved, applied, today, today),
				tally(saved, applied, weekStart, today), tally(saved, applied, weekStart.minusWeeks(1), weekStart.minusDays(1)),
				tally(saved, applied, monthStart, today),
				tally(saved, applied, monthStart.minusMonths(1), monthStart.minusDays(1)), recent(userId),
				followUps(userId, today));
	}

	private Map<LocalDate, Integer> counts(String sql, long userId, LocalDate from, LocalDate today, ZoneId zone) {
		Map<LocalDate, Integer> counts = new HashMap<>();
		this.jdbc.sql(sql)
			.param("userId", userId)
			.param("from", from)
			.param("today", today)
			.param("zone", zone.getId())
			.param("since", from.atStartOfDay(zone).toOffsetDateTime())
			.query((row, number) -> counts.put(row.getDate("day").toLocalDate(), row.getInt("jobs")))
			.list();
		return counts;
	}

	private static Tally tally(Map<LocalDate, Integer> saved, Map<LocalDate, Integer> applied, LocalDate from,
			LocalDate to) {
		int savedThen = 0;
		int appliedThen = 0;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			savedThen += saved.getOrDefault(day, 0);
			appliedThen += applied.getOrDefault(day, 0);
		}
		return new Tally(savedThen, appliedThen);
	}

	private List<Entry> recent(long userId) {
		return this.jdbc.sql(RECENT_ENTRIES)
			.param("userId", userId)
			.param("limit", RECENT)
			.query((row, number) -> new Entry(row.getLong("id"), row.getString("title"), row.getString("company"),
					ApplicationStatus.valueOf(row.getString("stage_category")), row.getString("stage_label"),
					row.getDate("event_date").toLocalDate(), row.getTimestamp("created_at").toInstant()))
			.list();
	}

	private List<FollowUpDue> followUps(long userId, LocalDate today) {
		List<String> ended = Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::hasEnded).map(Enum::name).toList();
		return this.jdbc.sql(FOLLOW_UPS_DUE)
			.param("userId", userId)
			.param("today", today)
			.param("ended", ended)
			.param("limit", FOLLOW_UPS)
			.query((row, number) -> new FollowUpDue(row.getLong("id"), row.getString("title"), row.getString("company"),
					row.getDate("follow_up_on").toLocalDate()))
			.list();
	}

}
