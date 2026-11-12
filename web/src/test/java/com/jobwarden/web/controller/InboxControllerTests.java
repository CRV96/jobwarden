package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.dto.NewMailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.enums.MailProblem;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.inbox.exception.InboxErrorCode;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@WebMvcTest(InboxController.class)
class InboxControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final EmailUpdate REJECTION = EmailUpdate.builder()
		.id(5)
		.receivedAt(Instant.parse("2026-10-30T12:05:00Z"))
		.sender("Ana Pop <ana@acme.com>")
		.subject("Your application to Acme")
		.excerpt("We have decided to move forward with other candidates.")
		.applicationId(7L)
		.jobTitle("Backend engineer")
		.company("Acme")
		.status(ApplicationStatus.REJECTED)
		.decision(UpdateDecision.PENDING)
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private InboxService inbox;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findAll(1, null)).willReturn(List.of(
				job(7, "Backend engineer", ApplicationStatus.APPLIED), job(8, "Platform engineer", ApplicationStatus.SAVED),
				job(9, "Old one", ApplicationStatus.REJECTED)));
	}

	@Test
	void offersToConnectAMailbox() {
		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.hasViewName("inbox/inbox")
			.bodyText()
			.contains("Inbox", "Connect your mailbox", "data-mail-provider", "data-host=\"imap.gmail.com\"",
					"name=\"password\"", "App password", "never sends, deletes or marks an email as read");
	}

	@Test
	void offersProtonMailBridgeOnThisComputer() {
		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.bodyText()
			.contains("Proton Mail (Bridge)", "data-host=\"127.0.0.1\" data-port=\"1143\" data-local=\"true\"",
					"Bridge password", "Proton Mail Bridge has to be open on this computer");
	}

	@Test
	void startsTheFormThatCameBackOnItsProvider() {
		willThrow(new BusinessRuleException(InboxErrorCode.LOCAL_SERVER_DOWN, "refused", "127.0.0.1", "1143"))
			.given(this.inbox).connect(eq(1L), any());

		assertThat(this.mvc.post()
			.uri("/inbox/account")
			.param("host", "127.0.0.1")
			.param("port", "1143")
			.param("username", "robert@proton.me")
			.param("password", "bridge password")
			.param("folder", "Labels/Jobs")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Nothing answers at 127.0.0.1, port 1143. If your mail comes through an app on this computer, such as Proton Mail Bridge",
					"data-local=\"true\"\n                        selected=\"selected\">Proton Mail (Bridge)",
					"value=\"127.0.0.1\"", "value=\"1143\"", "value=\"Labels/Jobs\"")
			.doesNotContain("bridge password");
	}

	@Test
	void saysWhatWentWrongWithBridgeInTheProfilesLanguage() {
		given(this.inbox.findAccount(1)).willReturn(Optional.of(new MailAccount("127.0.0.1", 1143, "robert@proton.me",
				"INBOX", "49:BF:C9", Instant.parse("2026-11-03T12:40:00Z"), "Connection refused",
				MailProblem.LOCAL_SERVER_DOWN, 10, InboxAutomation.ASK)));

		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.bodyText()
			.contains("nothing answered on this computer. If your mail comes through an app here, such as Proton Mail Bridge",
					"JobWarden trusts only the certificate it showed", "SHA-256 49:BF:C9")
			.doesNotContain("Connection refused");
	}

	@Test
	void listsTheEmailsWaitingWithTheJobTheyMatched() {
		given(this.inbox.findAccount(1)).willReturn(Optional.of(new MailAccount("imap.gmail.com", 993, "robert@gmail.com",
				"INBOX", null, Instant.parse("2026-11-02T17:40:00Z"), null, null, 10, InboxAutomation.ASK)));
		given(this.inbox.findPending(1)).willReturn(List.of(REJECTION));

		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.bodyText()
			.contains("robert@gmail.com", "Check now", "Your application to Acme", "Ana Pop &lt;ana@acme.com&gt;",
					"We have decided to move forward with other candidates.", "<option value=\"7\" selected=\"selected\">",
					"Platform engineer", "/inbox/updates/5/apply", "/inbox/updates/5/dismiss")
			// Only the jobs still in play
			.doesNotContain("Old one", "name=\"password\"");
	}

	@Test
	void showsWhyAMailboxCouldntBeConnectedWithoutItsPassword() {
		willThrow(new BusinessRuleException(InboxErrorCode.MAILBOX_NOT_REACHED, "refused", "Invalid credentials"))
			.given(this.inbox).connect(eq(1L), any());

		assertThat(this.mvc.post()
			.uri("/inbox/account")
			.param("host", "imap.gmail.com")
			.param("port", "993")
			.param("username", "robert@gmail.com")
			.param("password", "wrong password")
			.param("folder", "")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Couldn’t read the mailbox: Invalid credentials", "value=\"robert@gmail.com\"")
			.doesNotContain("wrong password");
		then(this.inbox).should().connect(1, new NewMailAccount("imap.gmail.com", 993, "robert@gmail.com", "wrong password",
				"INBOX"));
	}

	@Test
	void connectsAndReadsTheMailboxAtOnce() {
		assertThat(this.mvc.post()
			.uri("/inbox/account")
			.param("host", "imap.gmail.com")
			.param("port", "993")
			.param("username", "robert@gmail.com")
			.param("password", "app password")
			.param("folder", "INBOX")
			.header("HX-Request", "true")).hasStatusOk()
			.headers()
			.hasValue("HX-Refresh", "true");
		then(this.inbox).should().check(1);
	}

	@Test
	void appliesAnUpdateToTheJobPickedNotingTheEmail() {
		given(this.inbox.findPending(1)).willReturn(List.of(REJECTION));

		assertThat(this.mvc.post()
			.uri("/inbox/updates/5/apply")
			.param("job", "8")
			.param("status", "REJECTED")
			.header("HX-Request", "true")).hasStatusOk()
			.headers()
			.hasValue("HX-Trigger", "applications-changed");
		then(this.inbox).should().apply(1, 5, 8, ApplicationStatus.REJECTED,
				"From the email “Your application to Acme”, sent by Ana Pop <ana@acme.com>", UpdateDecider.YOU);
	}

	@Test
	void appliesNothingWithoutAJob() {
		given(this.inbox.findPending(1)).willReturn(List.of(REJECTION));

		assertThat(this.mvc.post().uri("/inbox/updates/5/apply").param("status", "REJECTED").header("HX-Request", "true"))
			.hasStatusOk();
		then(this.inbox).should(never()).apply(anyLong(), anyLong(), anyLong(), any(), any(), any());
	}

	@Test
	void saysHowOftenTheMailboxIsReadAndWhatBecomesOfTheUpdates() {
		given(this.inbox.findAccount(1)).willReturn(Optional.of(new MailAccount("imap.gmail.com", 993, "robert@gmail.com",
				"INBOX", null, null, null, null, 30, InboxAutomation.CLAUDE)));

		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.bodyText()
			.contains("Read every 30 minutes.", "JobWarden applies those it matches to a job; the assistant sorts the others.",
					"href=\"/settings#inbox\"");
	}

	@Test
	void asksClaudeToSortTheUpdatesWaiting() {
		given(this.inbox.findPending(1)).willReturn(List.of(REJECTION));
		given(this.discovery.findLatestRequest(1, AiRequestKind.SORT_INBOX)).willReturn(Optional.empty(),
				Optional.of(sorting(AiRequestStatus.WAITING)));

		assertThat(this.mvc.post().uri("/inbox/sort").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Do JobWarden request 9: call get_request with request_id 9 and profile_id 1",
					"hx-get=\"/inbox/sort\" hx-trigger=\"every 5s\"", "Waiting for the AI Assistant");
		then(this.discovery).should().createRequest(1, AiRequestKind.SORT_INBOX, AiRequirements.NONE);
	}

	@Test
	void offersClaudeTheUpdatesWaitingButOnlyOnce() {
		given(this.inbox.findPending(1)).willReturn(List.of(REJECTION));
		given(this.discovery.findLatestRequest(1, AiRequestKind.SORT_INBOX))
			.willReturn(Optional.of(sorting(AiRequestStatus.WORKING)));

		assertThat(this.mvc.post().uri("/inbox/sort").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("The AI Assistant is on it")
			.doesNotContain("Ask the AI Assistant to sort them");
		then(this.discovery).should(never()).createRequest(anyLong(), any(AiRequestKind.class), any(AiRequirements.class));
	}

	@Test
	void loadsTheUpdatesAnewOnceClaudeIsDone() {
		given(this.discovery.findLatestRequest(1, AiRequestKind.SORT_INBOX))
			.willReturn(Optional.of(sorting(AiRequestStatus.DONE)));

		assertThat(this.mvc.get().uri("/inbox/sort").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("hx-get=\"/inbox/updates\" hx-trigger=\"load\"", "The assistant, the last time: Applied 3, dismissed 1.")
			.doesNotContain("every 5s");
	}

	@Test
	void showsWhatJobWardenAndClaudeDidOnTheirOwn() {
		given(this.inbox.findDecidedForYou(eq(1L), any())).willReturn(List.of(
				REJECTION.toBuilder().decision(UpdateDecision.APPLIED).decidedBy(UpdateDecider.JOBWARDEN)
					.decidedAt(Instant.now()).build(),
				REJECTION.toBuilder().id(6).subject("Weekly jobs for you").applicationId(null).decision(UpdateDecision.DISMISSED)
					.decidedBy(UpdateDecider.CLAUDE).decidedAt(Instant.now()).build()));

		assertThat(this.mvc.get().uri("/inbox")).hasStatusOk()
			.bodyText()
			.contains("Done for you lately", "Done by JobWarden, on its own", "href=\"/applications/7\"",
					"Done by the AI Assistant", "Weekly jobs for you", "/inbox/updates/6/reopen", "Bring back");
	}

	@Test
	void bringsBackAnUpdateClaudeDismissed() {
		assertThat(this.mvc.post().uri("/inbox/updates/6/reopen").header("HX-Request", "true")).hasStatusOk();
		then(this.inbox).should().reopen(1, 6);
	}

	private static AiRequest sorting(AiRequestStatus status) {
		return new AiRequest(9, AiRequestKind.SORT_INBOX, AiRequirements.NONE, status,
				(status == AiRequestStatus.DONE) ? "Applied 3, dismissed 1." : null, 0, Instant.now(), null, null, null);
	}

	private static ApplicationSummary job(long id, String title, ApplicationStatus status) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.company("Acme")
			.status(status)
			.capturedAt(Instant.now())
			.build();
	}

}
