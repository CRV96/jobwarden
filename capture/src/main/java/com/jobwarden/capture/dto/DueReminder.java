package com.jobwarden.capture.dto;

/**
 * A reminder that's due, as the extension's notification says it.
 *
 * @param id the reminder, so a notification replaces one for the same reminder
 * @param applicationId the job, whose page a click on the notification opens
 * @param title what's coming and when, e.g. Technical interview · in 15 min
 * @param body the job, and how and where the interview takes place
 */
public record DueReminder(long id, long applicationId, String title, String body) {
}
