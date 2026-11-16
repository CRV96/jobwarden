package com.jobwarden.jobs.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * What the profile did lately, for the homepage: jobs saved and applications sent day by day, so far today, this week
 * and this month (and the week and the month before, to compare), the latest entries on the jobs' timelines, and the
 * follow-ups due.
 *
 * @param days the last {@link #DAYS} days, the oldest first, today last
 * @param recent the latest timeline entries, the last added first
 * @param followUps the jobs still going whose follow-up day has come, the longest due first
 */
public record JobActivity(List<Day> days, Tally today, Tally week, Tally lastWeek, Tally month, Tally lastMonth,
		List<Entry> recent, List<FollowUpDue> followUps) {

	/** How many days the chart shows. */
	public static final int DAYS = 30;

	public JobActivity {
		days = List.copyOf(days);
		recent = List.copyOf(recent);
		followUps = List.copyOf(followUps);
	}

	/**
	 * @return the jobs saved over the days of the chart
	 */
	public int totalSaved() {
		return this.days.stream().mapToInt(Day::saved).sum();
	}

	/**
	 * @return the applications sent over the days of the chart
	 */
	public int totalApplied() {
		return this.days.stream().mapToInt(Day::applied).sum();
	}

	/**
	 * @return the most saved on a day of the chart, at least 1: its scale
	 */
	public int mostSaved() {
		return Math.max(1, this.days.stream().mapToInt(Day::saved).max().orElse(0));
	}

	/**
	 * @return the most sent on a day of the chart, at least 1
	 */
	public int mostApplied() {
		return Math.max(1, this.days.stream().mapToInt(Day::applied).max().orElse(0));
	}

	/**
	 * @param saved the jobs saved that day, from a posting or typed in
	 * @param applied the applications sent that day, as their timelines say
	 */
	public record Day(LocalDate date, int saved, int applied) {
	}

	/**
	 * The jobs saved and the applications sent in a stretch of days.
	 */
	public record Tally(int saved, int applied) {

		public static final Tally NONE = new Tally(0, 0);

	}

	/**
	 * An entry on a job's timeline.
	 *
	 * @param date the day it happened on
	 * @param addedAt when it was added to the timeline
	 */
	public record Entry(long applicationId, String title, @Nullable String company, ApplicationStatus status,
			@Nullable String stageLabel, LocalDate date, Instant addedAt) {
	}

	/**
	 * A job to follow up on.
	 *
	 * @param on the day it was due
	 */
	public record FollowUpDue(long applicationId, String title, @Nullable String company, LocalDate on) {
	}

}
