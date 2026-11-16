package com.jobwarden.inbox.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.inbox.dto.CheckResult;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.dto.NewMailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * Recruiters' emails, as updates of the profile's jobs: JobWarden reads the profile's mailbox as often as it says, and
 * every email that looks like news about one of its jobs becomes an update, to apply to the job's timeline or dismiss.
 * Which the profile does, unless it said JobWarden may apply those it matched to a job, and Claude sort the others
 * ({@link InboxAutomation}). Every method takes the owning user's id, so one profile can never read another's mailbox
 * or updates.
 */
public interface InboxService {

	/**
	 * @return the profile's mailbox, without its password; empty when it has none
	 */
	Optional<MailAccount> findAccount(long userId);

	/**
	 * Signs in to the mailbox to check it, then keeps it, its password encrypted, in place of the one there was. It's
	 * read from a few weeks back the first time.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the mailbox can't be read, with the server's
	 * reason, e.g. a refused password
	 */
	MailAccount connect(long userId, NewMailAccount account);

	/**
	 * Forgets the mailbox and its password. The updates found stay.
	 */
	void disconnect(long userId);

	/**
	 * Reads the emails that came since the last time, now.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile has no mailbox, or it can't be read:
	 * then the mailbox keeps why, for the page to show
	 */
	CheckResult check(long userId);

	/**
	 * Reads the mailboxes whose time has come, as the schedule does: each as often as its profile said. One that can't
	 * be read keeps why, and the others go on.
	 */
	void checkDue();

	/**
	 * How often the mailbox is read on its own, and what becomes of the updates found. Those waiting now follow the new
	 * choice at once: JobWarden applies the ones it matched to a job, and asks Claude to sort the others, when the
	 * profile says so.
	 *
	 * @param readEveryMinutes from {@value #MIN_READ_EVERY_MINUTES} to {@value #MAX_READ_EVERY_MINUTES}; 0 for only when
	 * the profile asks
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile has no mailbox
	 * @throws IllegalArgumentException for another number of minutes
	 */
	MailAccount changeSettings(long userId, int readEveryMinutes, InboxAutomation automation);

	/** The most often a mailbox is read on its own, and the least: once a week. */
	int MIN_READ_EVERY_MINUTES = 5;

	int MAX_READ_EVERY_MINUTES = 7 * 24 * 60;

	/**
	 * @return the updates waiting for the profile, the newest first
	 */
	List<EmailUpdate> findPending(long userId);

	/**
	 * @return the updates JobWarden or Claude applied or dismissed on their own since then, the latest first, for the
	 * profile to look over
	 */
	List<EmailUpdate> findDecidedForYou(long userId, Instant since);

	long countPending(long userId);

	/**
	 * Adds the update to the job's timeline, dated when the email came: the job moves to the status, as with the status
	 * buttons.
	 *
	 * @param applicationId the job: the one the email matched, or another the profile picked
	 * @param status what the email says, or what the profile made of it
	 * @param note for the timeline, e.g. which email it was; {@code null} for JobWarden's own, which says it in no
	 * language: the subject and the sender
	 * @param by the profile, or Claude sorting the inbox
	 * @throws com.jobwarden.inbox.exception.EmailUpdateNotFoundException if the profile has no such update waiting
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 */
	void apply(long userId, long updateId, long applicationId, ApplicationStatus status, @Nullable String note,
			UpdateDecider by);

	/**
	 * Not news after all: it goes, and reading the email again doesn't bring it back.
	 *
	 * @param by the profile, or Claude sorting the inbox
	 * @throws com.jobwarden.inbox.exception.EmailUpdateNotFoundException if the profile has no such update waiting
	 */
	void dismiss(long userId, long updateId, UpdateDecider by);

	/**
	 * Waiting again: one Claude dismissed that was news after all.
	 *
	 * @throws com.jobwarden.inbox.exception.EmailUpdateNotFoundException if the profile has no such dismissed update
	 */
	void reopen(long userId, long updateId);

}
