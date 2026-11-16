package com.jobwarden.jobs.service;

import java.util.List;
import java.util.Locale;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.ReminderNotice;
import com.jobwarden.jobs.dto.ReminderTarget;
import com.jobwarden.jobs.enums.ReminderLead;

/**
 * Reminders of what the calendar shows: an interview or another event of a job's timeline, the day its applications
 * close, the day to follow up on it. Each comes once, some time before its entry: the pages and the browser extension
 * ask for the reminders that are due, and whichever asks first shows them. Every method takes the owning user's id,
 * like {@link ApplicationService}.
 */
public interface ReminderService {

	/**
	 * The job's entries from today on, each with its reminders: its events, its deadline and its day to follow up.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 */
	List<CalendarEntry> findRemindable(long userId, long applicationId);

	/**
	 * Sets a reminder of the entry, or takes it away. Setting one it already has, or taking away one it hasn't, changes
	 * nothing.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the job has no such entry, or the entry can't
	 * have that reminder (minutes before an entry without a time of day)
	 */
	void set(long userId, ReminderTarget target, ReminderLead lead, boolean on);

	/**
	 * The reminders due now, whose entry hasn't started yet (or, without a time of day, is today), worded in the
	 * language. Each comes once for each start of its entry: asking again gives only those that came due since. Those
	 * whose entry is over or gone are deleted.
	 */
	List<ReminderNotice> claimDue(long userId, Locale locale);

	/**
	 * Deletes the reminders, of every profile, whose entry is over (a little after a timed one starts, the day after
	 * one without a time) or gone (its event removed, its date cleared). Claiming them deletes a profile's too.
	 *
	 * @return how many it deleted
	 */
	int deleteOver();

}
