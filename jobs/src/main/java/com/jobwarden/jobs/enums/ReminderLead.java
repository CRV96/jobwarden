package com.jobwarden.jobs.enums;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * How long before a calendar entry its reminder comes. An entry without a time of day (a deadline, a day to follow up,
 * an event without a time) starts at {@link #ALL_DAY_START} for its reminders, so one at the time comes that morning.
 */
public enum ReminderLead {

	AT_TIME(0),

	MINUTES_15(15),

	HOUR_1(60),

	DAY_1(24 * 60),

	WEEK_1(7 * 24 * 60);

	/** When an entry without a time of day starts, for its reminders. */
	public static final LocalTime ALL_DAY_START = LocalTime.of(9, 0);

	private final int minutes;

	ReminderLead(int minutes) {
		this.minutes = minutes;
	}

	public int minutes() {
		return this.minutes;
	}

	/**
	 * @return whether it fits an entry without a time of day: a quarter of an hour before nine in the morning doesn't
	 */
	public boolean fitsAllDay() {
		return this.minutes == 0 || this.minutes >= DAY_1.minutes;
	}

	/**
	 * @return the leads an entry can have, shortest first
	 */
	public static List<ReminderLead> choices(boolean allDay) {
		return Arrays.stream(values()).filter(lead -> !allDay || lead.fitsAllDay()).toList();
	}

}
