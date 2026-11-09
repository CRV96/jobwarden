/**
 * Inbox: recruiters' emails. Reads the profile's mailbox over IMAP now and then, with an app password it keeps
 * encrypted, matches each email to one of the profile's jobs (the jobs module) by the company and the title, and tells
 * a rejection, an interview, an offer or a confirmation of an application from the email's words, in the app's
 * languages. Each becomes an update the profile applies to the job's timeline or dismisses: none changes a job on its
 * own. Owns the {@code mail_accounts} and {@code email_updates} tables.
 * <p>
 * Public API: the {@code dto}, {@code enums}, {@code exception} and {@code service} packages, marked with
 * {@code @NamedInterface}. {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.inbox;
