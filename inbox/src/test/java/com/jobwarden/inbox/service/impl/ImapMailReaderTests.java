package com.jobwarden.inbox.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jobwarden.inbox.service.impl.MailReader.Fetched;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Against GreenMail's IMAP server, which has no TLS: the reader is told so.
 */
class ImapMailReaderTests {

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

	private final ImapMailReader reader = new ImapMailReader(5000, false);

	private GreenMailUser robert;

	private MailServer server;

	@BeforeEach
	void aMailbox() {
		this.robert = greenMail.setUser("robert@example.com", "robert", "app-password");
		this.server = new MailServer("127.0.0.1", ServerSetupTest.IMAP.getPort(), "robert", "app-password", "INBOX");
	}

	@Test
	void readsTheEmailsSinceThenAndOnlyTheNewOnesAfter() throws Exception {
		deliver("Ana <ana@acme.com>", "Your application", "Unfortunately, we decided to move forward with others.");
		deliver("hr@globex.de", "Interview", "<p>We'd like to <b>schedule an interview</b>.</p>");

		Fetched first = this.reader.fetch(this.server, null, null, Instant.now().minus(30, ChronoUnit.DAYS), 100);

		assertThat(first.messages()).hasSize(2);
		assertThat(first.messages().getFirst()).satisfies(message -> {
			assertThat(message.fromName()).isEqualTo("Ana");
			assertThat(message.fromAddress()).isEqualTo("ana@acme.com");
			assertThat(message.subject()).isEqualTo("Your application");
			assertThat(message.text()).contains("move forward with others");
			assertThat(message.messageId()).isNotBlank();
		});
		assertThat(first.messages().get(1).text()).contains("schedule an interview").doesNotContain("<b>");

		// Nothing new: nothing read, and the place kept
		Fetched none = this.reader.fetch(this.server, first.uidValidity(), first.lastUid(), Instant.now(), 100);
		assertThat(none.messages()).isEmpty();
		assertThat(none.lastUid()).isEqualTo(first.lastUid());

		deliver("jobs@initech.com", "Thanks for applying", "We received your application.");
		Fetched next = this.reader.fetch(this.server, first.uidValidity(), first.lastUid(), Instant.now(), 100);
		assertThat(next.messages()).extracting(MailMessage::subject).containsExactly("Thanks for applying");
		assertThat(next.lastUid()).isGreaterThan(first.lastUid());
	}

	@Test
	void takesTheNewestWhenThereAreTooMany() throws Exception {
		for (int number = 1; number <= 5; number++) {
			deliver("hr@acme.com", "Email " + number, "Text " + number);
		}

		assertThat(this.reader.fetch(this.server, null, null, Instant.now().minus(1, ChronoUnit.DAYS), 2).messages())
			.extracting(MailMessage::subject)
			.containsExactly("Email 4", "Email 5");
	}

	@Test
	void saysWhyItCouldntSignIn() {
		this.reader.test(this.server);

		MailServer wrongPassword = new MailServer("127.0.0.1", ServerSetupTest.IMAP.getPort(), "robert", "wrong", "INBOX");
		assertThatExceptionOfType(MailFailure.class).isThrownBy(() -> this.reader.test(wrongPassword));
	}

	private void deliver(String from, String subject, String body) throws Exception {
		MimeMessage message = GreenMailUtil.createTextEmail("robert@example.com", from, subject, body,
				greenMail.getSmtp().getServerSetup());
		if (body.startsWith("<")) {
			message.setContent(body, "text/html; charset=UTF-8");
			message.saveChanges();
		}
		this.robert.deliver(message);
	}

}
