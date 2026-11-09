package com.jobwarden.inbox.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.NewMailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.MailProblem;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.inbox.exception.EmailUpdateNotFoundException;
import com.jobwarden.inbox.exception.InboxErrorCode;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.inbox.service.impl.MailReader.Fetched;
import com.jobwarden.inbox.service.impl.MailReader.MailFailure;
import com.jobwarden.inbox.service.impl.MailReader.Tested;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * With the jobs module, and no mail server: what the mailbox gives is the test's own. In the module's internal package,
 * to give it.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class InboxServiceTests {

	private static final NewMailAccount GMAIL = new NewMailAccount("imap.gmail.com", 993, "robert@gmail.com",
			"app password", "INBOX");

	private static final NewMailAccount PROTON_BRIDGE = new NewMailAccount("127.0.0.1", 1143, "robert@proton.me",
			"bridge password", "Labels/Jobs");

	private static final String CERTIFICATE = "49:BF:C9:12:31:21:23:FA:CD:C1:93:4D:C6:44:61:8E:BD:91:7D:82:2E:CA:21:CD:"
			+ "E3:C2:8B:4D:7E:17:7E:79";

	private static final Instant RECEIVED = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	private InboxService inbox;

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private MailReader reader;

	@MockitoBean
	private DiscoveryService discovery;

	private long userId;

	private long otherUserId;

	private long acme;

	@BeforeEach
	void createProfiles() {
		given(this.reader.test(any())).willReturn(new Tested(false, null));
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
		this.acme = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Platform Engineer")
			.company("Acme")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.now().minusDays(10))
			.build());
	}

	@Test
	void keepsTheMailboxWithItsPasswordEncrypted() {
		this.inbox.connect(this.userId, GMAIL);

		assertThat(this.inbox.findAccount(this.userId)).hasValueSatisfying(account -> {
			assertThat(account.host()).isEqualTo("imap.gmail.com");
			assertThat(account.username()).isEqualTo("robert@gmail.com");
			assertThat(account.lastCheckedAt()).isNull();
		});
		assertThat(this.jdbc.sql("SELECT password FROM mail_accounts").query(String.class).single())
			.isNotBlank()
			.doesNotContain("app password");
		assertThat(this.inbox.findAccount(this.otherUserId)).isEmpty();
	}

	@Test
	void keepsNoMailboxItCouldntSignInTo() {
		willThrow(new MailFailure("[AUTHENTICATIONFAILED] Invalid credentials", new RuntimeException())).given(this.reader)
			.test(any());

		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.inbox.connect(this.userId, GMAIL))
			.satisfies(refused -> {
				assertThat(refused.getErrorCode()).isEqualTo(InboxErrorCode.MAILBOX_NOT_REACHED);
				assertThat(refused.getArguments()).containsExactly("[AUTHENTICATIONFAILED] Invalid credentials");
			});
		assertThat(this.inbox.findAccount(this.userId)).isEmpty();
	}

	@Test
	void readsProtonMailBridgeAsItWasConnected() {
		given(this.reader.test(any())).willReturn(new Tested(true, CERTIFICATE));
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(1, 1, List.of()));

		assertThat(this.inbox.connect(this.userId, PROTON_BRIDGE).certificate()).isEqualTo(CERTIFICATE);
		this.inbox.check(this.userId);

		// After STARTTLS, and trusting only the certificate it showed
		ArgumentCaptor<MailServer> server = ArgumentCaptor.forClass(MailServer.class);
		then(this.reader).should().fetch(server.capture(), any(), any(), any(), anyInt());
		assertThat(server.getValue()).satisfies(read -> {
			assertThat(read.starttls()).isTrue();
			assertThat(read.certificate()).isEqualTo(CERTIFICATE);
			assertThat(read.password()).isEqualTo("bridge password");
			assertThat(read.folder()).isEqualTo("Labels/Jobs");
		});
	}

	@Test
	void saysWhenProtonMailBridgeIsntOpen() {
		willThrow(new MailFailure("Connection refused", MailProblem.LOCAL_SERVER_DOWN, new RuntimeException()))
			.given(this.reader)
			.test(any());

		assertThatExceptionOfType(BusinessRuleException.class)
			.isThrownBy(() -> this.inbox.connect(this.userId, PROTON_BRIDGE))
			.satisfies(refused -> {
				assertThat(refused.getErrorCode()).isEqualTo(InboxErrorCode.LOCAL_SERVER_DOWN);
				assertThat(refused.getArguments()).containsExactly("127.0.0.1", "1143");
			});
		assertThat(this.inbox.findAccount(this.userId)).isEmpty();
	}

	@Test
	void keepsWhatWentWrongWhenItCanSayIt() {
		given(this.reader.test(any())).willReturn(new Tested(true, CERTIFICATE));
		this.inbox.connect(this.userId, PROTON_BRIDGE);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willThrow(new MailFailure(
				"The server on this computer shows another certificate", MailProblem.CERTIFICATE_CHANGED,
				new RuntimeException()));

		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.inbox.check(this.userId));
		assertThat(this.inbox.findAccount(this.userId).orElseThrow().lastProblem())
			.isEqualTo(MailProblem.CERTIFICATE_CHANGED);

		// Read the next time: nothing wrong any more
		willReturn(new Fetched(1, 1, List.of())).given(this.reader).fetch(any(), any(), any(), any(), anyInt());
		this.inbox.check(this.userId);
		assertThat(this.inbox.findAccount(this.userId).orElseThrow()).satisfies(account -> {
			assertThat(account.lastProblem()).isNull();
			assertThat(account.lastError()).isNull();
		});
	}

	@Test
	void turnsTheNewsAboutTheJobsIntoUpdatesOnce() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of(
				email("<1@acme.com>", "Your application to Acme",
						"Thank you for applying to Acme. Unfortunately, we have decided to move forward with other candidates."),
				email("<2@news.com>", "Our newsletter", "What's new this week."))));

		assertThat(this.inbox.check(this.userId).added()).isEqualTo(1);
		// The same emails again, e.g. read once more: nothing twice
		assertThat(this.inbox.check(this.userId).added()).isZero();

		assertThat(this.inbox.findPending(this.userId)).singleElement().satisfies(update -> {
			assertThat(update.status()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(update.applicationId()).isEqualTo(this.acme);
			assertThat(update.jobTitle()).isEqualTo("Platform Engineer");
			assertThat(update.sender()).isEqualTo("Ana <ana@acme.com>");
			assertThat(update.excerpt()).isEqualTo("Unfortunately, we have decided to move forward with other candidates.");
		});
		assertThat(this.inbox.countPending(this.userId)).isEqualTo(1);
		assertThat(this.inbox.findPending(this.otherUserId)).isEmpty();
		assertThat(this.inbox.findAccount(this.userId).orElseThrow().lastCheckedAt()).isNotNull();
		// Where it got to, for the next time
		assertThat(this.jdbc.sql("SELECT uid_validity, last_uid FROM mail_accounts")
			.query((row, number) -> List.of(row.getLong(1), row.getLong(2)))
			.single()).containsExactly(7L, 42L);
	}

	@Test
	void keepsWhyItCouldntReadTheMailbox() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt()))
			.willThrow(new MailFailure("Connection timed out", new RuntimeException()));

		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.inbox.check(this.userId));
		assertThat(this.inbox.findAccount(this.userId).orElseThrow().lastError()).isEqualTo("Connection timed out");
		// The schedule goes on with the others
		this.inbox.checkDue();
	}

	@Test
	void readsEachMailboxAsOftenAsItsProfileSaid() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of()));

		// Never read yet: due
		this.inbox.checkDue();
		// Read just now, every ten minutes: not yet
		this.inbox.checkDue();
		then(this.reader).should(times(1)).fetch(any(), any(), any(), any(), anyInt());

		// Only when the profile asks: never on its own
		this.inbox.changeSettings(this.userId, 0, InboxAutomation.ASK);
		this.jdbc.sql("UPDATE mail_accounts SET last_checked_at = now() - interval '1 day'").update();
		this.inbox.checkDue();
		then(this.reader).should(times(1)).fetch(any(), any(), any(), any(), anyInt());

		this.inbox.changeSettings(this.userId, 60, InboxAutomation.ASK);
		this.inbox.checkDue();
		then(this.reader).should(times(2)).fetch(any(), any(), any(), any(), anyInt());
		assertThat(this.inbox.findAccount(this.userId).orElseThrow().readEveryMinutes()).isEqualTo(60);
		assertThatIllegalArgumentException().isThrownBy(() -> this.inbox.changeSettings(this.userId, 2, InboxAutomation.ASK));
	}

	@Test
	void appliesTheUpdatesItMatchedOnItsOwnWhenTheProfileSaysSo() {
		this.inbox.connect(this.userId, GMAIL);
		this.inbox.changeSettings(this.userId, 10, InboxAutomation.MATCHED);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of(
				email("<1@acme.com>", "Your application", "We regret to inform you that the position at Acme has been filled."),
				new MailMessage("<2@globex.com>", RECEIVED, "Ion", "ion@globex.com", "Interview",
						"We'd like to invite you to an interview for the Data Engineer role."))));

		this.inbox.check(this.userId);

		ApplicationDetails job = this.applications.findById(this.userId, this.acme).orElseThrow();
		assertThat(job.status()).isEqualTo(ApplicationStatus.REJECTED);
		// Noted in no language: the subject and the sender
		assertThat(job.timeline().getLast().note()).isEqualTo("✉ “Your application” · Ana <ana@acme.com>");
		// Matched to no job: it waits, and Claude isn't asked
		assertThat(this.inbox.findPending(this.userId)).singleElement()
			.satisfies(update -> assertThat(update.subject()).isEqualTo("Interview"));
		then(this.discovery).should(never()).createRequest(anyLong(), any(AiRequestKind.class), any(AiRequirements.class));
		assertThat(this.inbox.findDecidedForYou(this.userId, Instant.now().minusSeconds(60))).singleElement()
			.satisfies(update -> {
				assertThat(update.decidedBy()).isEqualTo(UpdateDecider.JOBWARDEN);
				assertThat(update.decision()).isEqualTo(UpdateDecision.APPLIED);
				assertThat(update.applicationId()).isEqualTo(this.acme);
				assertThat(update.text()).contains("position at Acme has been filled");
			});
	}

	@Test
	void asksClaudeToSortTheOthersOnceWhenTheProfileSaysSo() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of(
				new MailMessage("<2@globex.com>", RECEIVED, "Ion", "ion@globex.com", "Interview",
						"We'd like to invite you to an interview for the Data Engineer role."))));
		this.inbox.check(this.userId);

		this.inbox.changeSettings(this.userId, 10, InboxAutomation.CLAUDE);

		then(this.discovery).should().createRequest(this.userId, AiRequestKind.SORT_INBOX, AiRequirements.NONE);
		// Asked already, and Claude isn't done: not again
		given(this.discovery.findOpenRequests(this.userId)).willReturn(List.of(new AiRequest(3, AiRequestKind.SORT_INBOX,
				AiRequirements.NONE, AiRequestStatus.WAITING, null, 0, Instant.now(), null, null, null)));
		this.inbox.check(this.userId);
		then(this.discovery).should(times(1)).createRequest(anyLong(), any(AiRequestKind.class), any(AiRequirements.class));
	}

	@Test
	void keepsTheJobAnUpdateWasAppliedTo() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of(
				new MailMessage("<2@globex.com>", RECEIVED, "Ion", "ion@globex.com", "Interview",
						"We'd like to invite you to an interview for the Data Engineer role."))));
		this.inbox.check(this.userId);
		long update = this.inbox.findPending(this.userId).getFirst().id();

		this.inbox.apply(this.userId, update, this.acme, ApplicationStatus.INTERVIEWING, null, UpdateDecider.CLAUDE);

		assertThat(this.inbox.findDecidedForYou(this.userId, Instant.now().minusSeconds(60))).singleElement()
			.satisfies(decided -> {
				assertThat(decided.applicationId()).isEqualTo(this.acme);
				assertThat(decided.jobTitle()).isEqualTo("Platform Engineer");
			});
	}

	@Test
	void bringsBackAnUpdateClaudeDismissed() {
		long update = pendingRejection();
		this.inbox.dismiss(this.userId, update, UpdateDecider.CLAUDE);

		assertThatExceptionOfType(EmailUpdateNotFoundException.class)
			.isThrownBy(() -> this.inbox.reopen(this.otherUserId, update));
		this.inbox.reopen(this.userId, update);

		assertThat(this.inbox.findPending(this.userId)).singleElement()
			.satisfies(waiting -> assertThat(waiting.decidedBy()).isNull());
	}

	@Test
	void appliesAnUpdateToTheJobsTimelineWhenTheEmailCame() {
		long update = pendingRejection();

		this.inbox.apply(this.userId, update, this.acme, ApplicationStatus.REJECTED, "From the email “Your application”",
				UpdateDecider.YOU);

		ApplicationDetails job = this.applications.findById(this.userId, this.acme).orElseThrow();
		LocalDateTime received = LocalDateTime.ofInstant(RECEIVED, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES);
		assertThat(job.status()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(job.timeline().getLast()).satisfies(event -> {
			assertThat(event.eventDate()).isEqualTo(received.toLocalDate());
			assertThat(event.eventTime()).isEqualTo(received.toLocalTime());
			assertThat(event.note()).isEqualTo("From the email “Your application”");
		});
		assertThat(this.inbox.findPending(this.userId)).isEmpty();
		assertThatExceptionOfType(EmailUpdateNotFoundException.class)
			.isThrownBy(() -> this.inbox.apply(this.userId, update, this.acme, ApplicationStatus.REJECTED, null,
					UpdateDecider.YOU));
	}

	@Test
	void dismissesAnUpdateForGood() {
		long update = pendingRejection();

		assertThatExceptionOfType(EmailUpdateNotFoundException.class)
			.isThrownBy(() -> this.inbox.dismiss(this.otherUserId, update, UpdateDecider.YOU));
		this.inbox.dismiss(this.userId, update, UpdateDecider.YOU);

		assertThat(this.inbox.findPending(this.userId)).isEmpty();
		assertThat(this.inbox.check(this.userId).added()).isZero();
		assertThat(this.applications.findById(this.userId, this.acme).orElseThrow().status())
			.isEqualTo(ApplicationStatus.APPLIED);
	}

	@Test
	void forgetsTheMailboxButNotItsUpdates() {
		pendingRejection();

		this.inbox.disconnect(this.userId);

		assertThat(this.inbox.findAccount(this.userId)).isEmpty();
		assertThat(this.inbox.findPending(this.userId)).hasSize(1);
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> this.inbox.check(this.userId))
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(InboxErrorCode.NO_MAILBOX));
	}

	private long pendingRejection() {
		this.inbox.connect(this.userId, GMAIL);
		given(this.reader.fetch(any(), any(), any(), any(), anyInt())).willReturn(new Fetched(7, 42, List.of(
				email("<1@acme.com>", "Your application", "We regret to inform you that the position at Acme has been filled."))));
		this.inbox.check(this.userId);
		return this.inbox.findPending(this.userId).stream().map(EmailUpdate::id).findFirst().orElseThrow();
	}

	private static MailMessage email(String id, String subject, String text) {
		return new MailMessage(id, RECEIVED, "Ana", "ana@acme.com", subject, text);
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id")
			.param(name)
			.query(Long.class)
			.single();
	}

}
