package com.jobwarden.inbox.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.inbox.dto.CheckResult;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.dto.NewMailAccount;
import com.jobwarden.inbox.entity.EmailUpdateEntity;
import com.jobwarden.inbox.entity.MailAccountEntity;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.MailProblem;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.inbox.exception.EmailUpdateNotFoundException;
import com.jobwarden.inbox.exception.InboxErrorCode;
import com.jobwarden.inbox.repository.EmailUpdateRepository;
import com.jobwarden.inbox.repository.MailAccountRepository;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.inbox.service.impl.JobEmails.Found;
import com.jobwarden.inbox.service.impl.MailReader.Fetched;
import com.jobwarden.inbox.service.impl.MailReader.Tested;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reading a mailbox talks to its server, which may take a while: that happens outside any transaction, and only what
 * it found is saved in one. Then what the profile said becomes of the updates waiting ({@link InboxAutomation}):
 * JobWarden applies those it matched to a job, oldest first, so the timeline tells them in order, and asks Claude to
 * sort the others with a request (the discover module), unless one is still open.
 */
@Service
@CustomLog
class DefaultInboxService implements InboxService {

	/** How far back the first reading goes: the news of the last few weeks. */
	static final Duration FIRST_READ = Duration.ofDays(30);

	/** The most emails one reading takes, the newest: a busy mailbox catches up over a few. */
	static final int MAX_PER_CHECK = 300;

	/** The jobs an email can be news about: those still in play, and those that never got an answer. */
	private static final Set<ApplicationStatus> IN_PLAY = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEWING, ApplicationStatus.OFFER, ApplicationStatus.POSITION_REOPENED,
			ApplicationStatus.NO_RESPONSE);

	private static final int SENDER_MAX_LENGTH = 300;

	private static final int SUBJECT_MAX_LENGTH = 500;

	private static final int ERROR_MAX_LENGTH = 500;

	/** How much of an email an update keeps, for Claude to tell which job it's about: the news comes first. */
	static final int TEXT_MAX_LENGTH = 3000;

	private final MailAccountRepository accounts;

	private final EmailUpdateRepository updates;

	private final ApplicationService applications;

	private final MailReader reader;

	private final SecretBox secrets;

	private final DiscoveryService discovery;

	private final TransactionTemplate transactions;

	DefaultInboxService(MailAccountRepository accounts, EmailUpdateRepository updates, ApplicationService applications,
			MailReader reader, SecretBox secrets, DiscoveryService discovery, PlatformTransactionManager transactionManager) {
		this.accounts = accounts;
		this.updates = updates;
		this.applications = applications;
		this.reader = reader;
		this.secrets = secrets;
		this.discovery = discovery;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Override
	public Optional<MailAccount> findAccount(long userId) {
		return this.accounts.findById(userId).map(DefaultInboxService::toAccount);
	}

	/**
	 * Keeps how the server could be read: after STARTTLS or not, and for a server on this computer, the certificate
	 * it showed, the only one trusted after.
	 *
	 * @throws BusinessRuleException if the mailbox can't be read
	 */
	@Override
	public MailAccount connect(long userId, NewMailAccount account) {
		MailServer server = new MailServer(account.host().strip(), account.port(), account.username().strip(),
				account.password(), account.folder().strip());
		Tested tested;
		try {
			tested = this.reader.test(server);
		} catch (MailFailure failed) {
			log.debug("Mailbox of profile {} not connected: {}", userId, failed.getMessage());
			if (failed.problem() == MailProblem.LOCAL_SERVER_DOWN) {
				// The port as typed, not as a number: 1143, not 1,143
				throw new BusinessRuleException(InboxErrorCode.LOCAL_SERVER_DOWN, "Nothing answers at " + server,
						server.host(), Integer.toString(server.port()));
			}
			throw new BusinessRuleException(InboxErrorCode.MAILBOX_NOT_REACHED, "Couldn't read " + server,
					shortened(failed.getMessage(), ERROR_MAX_LENGTH));
		}
		MailAccountEntity saved = this.transactions.execute(status -> {
			MailAccountEntity entity = this.accounts.findById(userId).orElseGet(() -> new MailAccountEntity(userId));
			entity.connect(server.host(), server.port(), server.username(), this.secrets.encrypt(server.password()),
					server.folder(), tested.starttls(), tested.certificate());
			return this.accounts.save(entity);
		});
		log.info("Connected the mailbox of profile {}{}", userId, tested.starttls() ? ", with STARTTLS" : "");
		return toAccount(saved);
	}

	@Override
	@Transactional
	public void disconnect(long userId) {
		if (this.accounts.existsById(userId)) {
			this.accounts.deleteById(userId);
			log.info("Disconnected the mailbox of profile {}", userId);
		}
	}

	/**
	 * @throws BusinessRuleException if the profile has no mailbox, or it can't be read
	 */
	@Override
	public CheckResult check(long userId) {
		MailAccountEntity account = this.accounts.findById(userId)
			.orElseThrow(() -> new BusinessRuleException(InboxErrorCode.NO_MAILBOX, "Profile " + userId + " has no mailbox"));
		Fetched fetched;
		try {
			MailServer server = new MailServer(account.getHost(), account.getPort(), account.getUsername(),
					this.secrets.decrypt(account.getPassword()), account.getFolder(), account.isStarttls(),
					account.getCertificate());
			fetched = this.reader.fetch(server, account.getUidValidity(), account.getLastUid(),
					Instant.now().minus(FIRST_READ), MAX_PER_CHECK);
		} catch (MailFailure | IllegalStateException failed) {
			String reason = shortened(failed.getMessage(), ERROR_MAX_LENGTH);
			MailProblem problem = (failed instanceof MailFailure mail) ? mail.problem() : null;
			this.transactions.executeWithoutResult(status -> this.accounts.findById(userId)
				.ifPresent(stillThere -> stillThere.failed(reason, problem, Instant.now())));
			log.warn("Couldn't read the mailbox of profile {}: {}", userId, reason);
			throw new BusinessRuleException(InboxErrorCode.MAILBOX_NOT_REACHED, "Couldn't read the mailbox of profile "
					+ userId, reason);
		}
		List<JobEmails.Prepared> jobs = JobEmails.prepare(jobsInPlay(userId));
		CheckResult result = this.transactions.execute(status -> {
			int added = 0;
			for (MailMessage message : fetched.messages()) {
				if (this.updates.existsByUserIdAndMessageId(userId, message.messageId())) {
					continue;
				}
				Optional<Found> found = JobEmails.read(message, jobs);
				if (found.isPresent()) {
					this.updates.save(EmailUpdateEntity.builder()
						.userId(userId)
						.messageId(message.messageId())
						.receivedAt(message.receivedAt())
						.sender(shortened(sender(message), SENDER_MAX_LENGTH))
						.subject(shortened(message.subject(), SUBJECT_MAX_LENGTH))
						.excerpt(found.get().excerpt())
						.text(shortened(message.text().strip(), TEXT_MAX_LENGTH))
						.applicationId(found.get().applicationId())
						.status(found.get().status())
						.build());
					added++;
				}
			}
			this.accounts.findById(userId)
				.ifPresent(stillThere -> stillThere.read(fetched.uidValidity(), fetched.lastUid(), Instant.now()));
			return new CheckResult(fetched.messages().size(), added);
		});
		log.info("Read {} emails of profile {}: {} updates", result.read(), userId, result.added());
		automate(userId);
		return result;
	}

	@Override
	public void checkDue() {
		Instant now = Instant.now();
		for (MailAccountEntity account : this.accounts.findAll()) {
			if (!account.isDue(now)) {
				continue;
			}
			try {
				check(account.getUserId());
			} catch (BusinessRuleException failed) {
				// Kept as the mailbox's last error, which the page shows
			}
		}
	}

	/**
	 * @throws BusinessRuleException if the profile has no mailbox
	 */
	@Override
	public MailAccount changeSettings(long userId, int readEveryMinutes, InboxAutomation automation) {
		if (readEveryMinutes != 0 && (readEveryMinutes < MIN_READ_EVERY_MINUTES || readEveryMinutes > MAX_READ_EVERY_MINUTES)) {
			throw new IllegalArgumentException("Not between " + MIN_READ_EVERY_MINUTES + " and " + MAX_READ_EVERY_MINUTES
					+ " minutes: " + readEveryMinutes);
		}
		MailAccount changed = this.transactions.execute(status -> {
			MailAccountEntity account = this.accounts.findById(userId)
				.orElseThrow(() -> new BusinessRuleException(InboxErrorCode.NO_MAILBOX, "Profile " + userId + " has no mailbox"));
			account.changeSettings(readEveryMinutes, automation);
			return toAccount(account);
		});
		log.info("Profile {} reads its mailbox every {} minutes, {}", userId, readEveryMinutes, automation);
		automate(userId);
		return changed;
	}

	/**
	 * What the profile said becomes of the updates waiting: JobWarden applies those it matched to a job, oldest first,
	 * and asks Claude to sort the others, unless a request to is still open.
	 */
	private void automate(long userId) {
		InboxAutomation automation = this.accounts.findById(userId)
			.map(MailAccountEntity::getAutomation)
			.orElse(InboxAutomation.ASK);
		if (!automation.appliesMatched()) {
			return;
		}
		List<EmailUpdateEntity> waiting = this.updates.findByUserIdAndDecisionOrderByReceivedAtDescIdDesc(userId,
				UpdateDecision.PENDING);
		int applied = 0;
		for (EmailUpdateEntity update : waiting.reversed()) {
			Long job = update.getApplicationId();
			// Matched to no job, or to one archived since: it waits for the profile
			if (job == null || this.applications.findById(userId, job).filter(ApplicationDetails::isArchived).isPresent()) {
				continue;
			}
			try {
				this.transactions.executeWithoutResult(status -> applyPending(userId, update.getId(), job, update.getStatus(),
						null, UpdateDecider.JOBWARDEN));
				applied++;
			} catch (ApplicationNotFoundException | EmailUpdateNotFoundException gone) {
				// Deleted, or decided, in the meantime: nothing to apply
			}
		}
		if (applied > 0) {
			log.info("Applied {} email updates of profile {} on its own", applied, userId);
		}
		if (automation == InboxAutomation.CLAUDE && countPending(userId) > 0 && this.discovery.findOpenRequests(userId)
			.stream()
			.noneMatch(request -> request.kind() == AiRequestKind.SORT_INBOX)) {
			this.discovery.createRequest(userId, AiRequestKind.SORT_INBOX, AiRequirements.NONE);
		}
	}

	@Override
	public List<EmailUpdate> findPending(long userId) {
		return this.updates.findByUserIdAndDecisionOrderByReceivedAtDescIdDesc(userId, UpdateDecision.PENDING)
			.stream()
			.map(update -> toUpdate(userId, update))
			.toList();
	}

	@Override
	public long countPending(long userId) {
		return this.updates.countByUserIdAndDecision(userId, UpdateDecision.PENDING);
	}

	@Override
	public List<EmailUpdate> findDecidedForYou(long userId, Instant since) {
		return this.updates.findByUserIdAndDecidedByInAndDecidedAtAfterOrderByDecidedAtDescIdDesc(userId,
				EnumSet.of(UpdateDecider.JOBWARDEN, UpdateDecider.CLAUDE), since)
			.stream()
			.map(update -> toUpdate(userId, update))
			.toList();
	}

	/**
	 * @throws EmailUpdateNotFoundException if the profile has no such update waiting
	 */
	@Override
	@Transactional
	public void apply(long userId, long updateId, long applicationId, ApplicationStatus status, @Nullable String note,
			UpdateDecider by) {
		applyPending(userId, updateId, applicationId, status, note, by);
	}

	/** In the transaction there is: the service's own, or the one automate opens for each. */
	private void applyPending(long userId, long updateId, long applicationId, ApplicationStatus status,
			@Nullable String note, UpdateDecider by) {
		EmailUpdateEntity update = pending(userId, updateId);
		LocalDateTime received = LocalDateTime.ofInstant(update.getReceivedAt(), ZoneId.systemDefault())
			.truncatedTo(ChronoUnit.MINUTES);
		NewTimelineEvent event = NewTimelineEvent.builder()
			.eventDate(received.toLocalDate())
			.eventTime(received.toLocalTime())
			.stageCategory(status)
			.note((note != null) ? note : emailNote(update))
			.build();
		// The job may say so already, e.g. an application confirmed after it was marked applied: nothing new to add
		if (this.applications.wouldRepeat(userId, applicationId, event)) {
			log.info("Email update {} says application {} of profile {} is {}, as its timeline does already", updateId,
					applicationId, userId, status);
		} else {
			this.applications.addEvent(userId, applicationId, event);
		}
		update.applyTo(applicationId, by);
		log.info("Applied email update {} to application {} of profile {} as {} ({})", updateId, applicationId, userId,
				status, by);
	}

	/**
	 * @throws EmailUpdateNotFoundException if the profile has no such update waiting
	 */
	@Override
	@Transactional
	public void dismiss(long userId, long updateId, UpdateDecider by) {
		pending(userId, updateId).decide(UpdateDecision.DISMISSED, by);
		log.info("Dismissed email update {} of profile {} ({})", updateId, userId, by);
	}

	/**
	 * @throws EmailUpdateNotFoundException if the profile has no such dismissed update
	 */
	@Override
	@Transactional
	public void reopen(long userId, long updateId) {
		this.updates.findByIdAndUserIdAndDecision(updateId, userId, UpdateDecision.DISMISSED)
			.orElseThrow(() -> new EmailUpdateNotFoundException(updateId))
			.reopen();
		log.info("Email update {} of profile {} waits again", updateId, userId);
	}

	/**
	 * Which email it was, for the timeline, in no language: the subject in quotes and the sender, e.g. ✉ “Your
	 * application to Acme” · Ana Pop &lt;ana@acme.com&gt;.
	 */
	static String emailNote(EmailUpdateEntity update) {
		StringBuilder note = new StringBuilder("✉");
		if (update.getSubject() != null && !update.getSubject().isBlank()) {
			note.append(" “").append(update.getSubject().strip()).append('”');
		}
		if (update.getSender() != null) {
			note.append((note.length() > 1) ? " · " : " ").append(update.getSender());
		}
		return note.toString();
	}

	private EmailUpdateEntity pending(long userId, long updateId) {
		return this.updates.findByIdAndUserIdAndDecision(updateId, userId, UpdateDecision.PENDING)
			.orElseThrow(() -> new EmailUpdateNotFoundException(updateId));
	}

	/**
	 * The jobs an email can be about, those changed last first: the likelier, among several at one company. Each status
	 * apart, as those lists leave the archived jobs out: an email never changes one.
	 */
	private List<JobEmails.Job> jobsInPlay(long userId) {
		return IN_PLAY.stream()
			.flatMap(status -> this.applications.findAll(userId, status).stream())
			.sorted(Comparator.comparing(ApplicationSummary::lastModified).reversed())
			.map(job -> new JobEmails.Job(job.id(), job.title(), job.company(), job.status()))
			.toList();
	}

	private EmailUpdate toUpdate(long userId, EmailUpdateEntity update) {
		Optional<ApplicationDetails> job = (update.getApplicationId() != null)
				? this.applications.findById(userId, update.getApplicationId()) : Optional.empty();
		return EmailUpdate.builder()
			.id(update.getId())
			.receivedAt(update.getReceivedAt())
			.sender(update.getSender())
			.subject(update.getSubject())
			.excerpt(update.getExcerpt())
			.text(update.getText())
			.applicationId(job.map(ApplicationDetails::id).orElse(null))
			.jobTitle(job.map(ApplicationDetails::title).orElse(null))
			.company(job.map(ApplicationDetails::company).orElse(null))
			.status(update.getStatus())
			.decision(update.getDecision())
			.decidedBy(update.getDecidedBy())
			.decidedAt(update.getDecidedAt())
			.build();
	}

	private static MailAccount toAccount(MailAccountEntity account) {
		return new MailAccount(account.getHost(), account.getPort(), account.getUsername(), account.getFolder(),
				account.getCertificate(), account.getLastCheckedAt(), account.getLastError(), account.getLastProblem(),
				account.getReadEveryMinutes(), account.getAutomation());
	}

	private static @Nullable String sender(MailMessage message) {
		if (message.fromName() != null && message.fromAddress() != null) {
			return message.fromName() + " <" + message.fromAddress() + ">";
		}
		return (message.fromAddress() != null) ? message.fromAddress() : message.fromName();
	}

	private static @Nullable String shortened(@Nullable String text, int maxLength) {
		return (text == null || text.length() <= maxLength) ? text : text.substring(0, maxLength - 1) + "…";
	}

}
