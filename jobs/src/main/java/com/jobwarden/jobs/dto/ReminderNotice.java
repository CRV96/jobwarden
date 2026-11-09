package com.jobwarden.jobs.dto;

import com.jobwarden.jobs.enums.CalendarEntryKind;

/**
 * A reminder that's due, as a notification says it, in the profile's language.
 *
 * @param id the reminder, e.g. so a notification replaces another for the same one
 * @param title what's coming and when, e.g. Technical interview in 15 minutes
 * @param body the job, and the details of the entry, e.g. Backend engineer at Acme · 14:00 · Video call
 */
public record ReminderNotice(long id, long applicationId, CalendarEntryKind kind, String title, String body) {
}
