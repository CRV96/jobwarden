package com.jobwarden.inbox.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.DummySSLServerSocketFactory;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jobwarden.inbox.enums.MailProblem;
import com.jobwarden.inbox.service.impl.MailReader.MailFailure;
import com.jobwarden.inbox.service.impl.MailReader.Tested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * TLS as Proton Mail Bridge has it: a server on this computer with a certificate it signed itself, over TLS from the
 * start (GreenMail's IMAPS) or after STARTTLS (a server here that does as Bridge does by default).
 */
class ImapMailReaderTlsTests {

	private static final String FINGERPRINT = "[0-9A-F]{2}(:[0-9A-F]{2}){31}";

	/** A fingerprint no certificate has. */
	private static final String ANOTHER = "00" + ":00".repeat(31);

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(
			new ServerSetup[] { ServerSetupTest.SMTP, ServerSetupTest.IMAP, ServerSetupTest.IMAPS });

	private final ImapMailReader reader = new ImapMailReader(5000, true);

	@Test
	void trustsTheCertificateAServerOnThisComputerShowedWhenItWasConnected() throws Exception {
		greenMail.setUser("robert@proton.me", "robert@proton.me", "bridge-password")
			.deliver(GreenMailUtil.createTextEmail("robert@proton.me", "hr@acme.com", "Your application",
					"We received your application.", greenMail.getSmtp().getServerSetup()));
		MailServer server = local(ServerSetupTest.IMAPS.getPort());

		Tested tested = this.reader.test(server);

		assertThat(tested.starttls()).isFalse();
		assertThat(tested.certificate()).matches(FINGERPRINT);
		MailServer connected = new MailServer(server.host(), server.port(), server.username(), server.password(),
				server.folder(), tested.starttls(), tested.certificate());
		assertThat(this.reader.fetch(connected, null, null, Instant.now().minus(1, ChronoUnit.DAYS), 10).messages())
			.extracting(MailMessage::subject)
			.containsExactly("Your application");
		// Another certificate, after: not the server it was
		MailServer changed = new MailServer(server.host(), server.port(), server.username(), server.password(),
				server.folder(), false, ANOTHER);
		assertThatExceptionOfType(MailFailure.class)
			.isThrownBy(() -> this.reader.fetch(changed, null, null, Instant.now(), 10))
			.satisfies(failed -> assertThat(failed.problem()).isEqualTo(MailProblem.CERTIFICATE_CHANGED));
	}

	@Test
	void turnsTlsOnWithStarttlsForAServerThatStartsWithoutIt() throws Exception {
		try (StarttlsServer bridge = new StarttlsServer()) {
			MailServer server = local(bridge.port());

			Tested tested = this.reader.test(server);

			assertThat(tested.starttls()).isTrue();
			assertThat(tested.certificate()).matches(FINGERPRINT);
			assertThat(bridge.signedInOverTls).isTrue();
			// After STARTTLS too, only that certificate
			MailServer changed = new MailServer(server.host(), server.port(), server.username(), server.password(),
					server.folder(), true, ANOTHER);
			assertThatExceptionOfType(MailFailure.class)
				.isThrownBy(() -> this.reader.fetch(changed, null, null, Instant.now(), 10))
				.satisfies(failed -> assertThat(failed.problem()).isEqualTo(MailProblem.CERTIFICATE_CHANGED));
		}
	}

	@Test
	void neverReadsAServerWithoutTls() {
		greenMail.setUser("robert@example.com", "robert", "app-password");

		assertThatExceptionOfType(MailFailure.class)
			.isThrownBy(() -> this.reader.test(new MailServer("127.0.0.1", ServerSetupTest.IMAP.getPort(), "robert",
					"app-password", "INBOX")))
			.withMessageContaining("STARTTLS");
	}

	@Test
	void saysWhenNothingAnswersOnThisComputer() throws IOException {
		int port;
		try (ServerSocket nobody = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = nobody.getLocalPort();
		}

		assertThatExceptionOfType(MailFailure.class).isThrownBy(() -> this.reader.test(local(port)))
			.satisfies(failed -> assertThat(failed.problem()).isEqualTo(MailProblem.LOCAL_SERVER_DOWN));
	}

	private static MailServer local(int port) {
		return new MailServer("127.0.0.1", port, "robert@proton.me", "bridge-password", "INBOX");
	}

	/**
	 * IMAP as Proton Mail Bridge serves it by default: without TLS until STARTTLS, then with a certificate of its own
	 * (GreenMail's), signing in only after. Enough of IMAP to sign in and open a folder.
	 */
	private static final class StarttlsServer implements AutoCloseable {

		private final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());

		private final ExecutorService connections = Executors.newCachedThreadPool();

		private final SSLContext tls;

		private volatile boolean signedInOverTls;

		StarttlsServer() throws Exception {
			KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keys.init(new DummySSLServerSocketFactory().getKeyStore(), "changeit".toCharArray());
			this.tls = SSLContext.getInstance("TLS");
			this.tls.init(keys.getKeyManagers(), null, null);
			this.connections.submit(this::accept);
		}

		int port() {
			return this.socket.getLocalPort();
		}

		private void accept() {
			while (!this.socket.isClosed()) {
				try {
					Socket client = this.socket.accept();
					this.connections.submit(() -> talk(client));
				} catch (IOException closed) {
					return;
				}
			}
		}

		private void talk(Socket client) {
			Socket connection = client;
			boolean secured = false;
			try {
				BufferedReader in = reader(connection);
				Writer out = writer(connection);
				send(out, "* OK [CAPABILITY IMAP4rev1 STARTTLS LOGINDISABLED] Ready");
				for (String line = in.readLine(); line != null; line = in.readLine()) {
					String[] words = line.split(" ", 3);
					String tag = words[0];
					String command = (words.length > 1) ? words[1].toUpperCase() : "";
					switch (command) {
						case "CAPABILITY" -> {
							send(out, secured ? "* CAPABILITY IMAP4rev1" : "* CAPABILITY IMAP4rev1 STARTTLS LOGINDISABLED");
							send(out, tag + " OK CAPABILITY completed");
						}
						case "STARTTLS" -> {
							send(out, tag + " OK Begin TLS negotiation now");
							SSLSocket upgraded = (SSLSocket) this.tls.getSocketFactory()
								.createSocket(connection, null, connection.getPort(), true);
							upgraded.setUseClientMode(false);
							upgraded.startHandshake();
							connection = upgraded;
							secured = true;
							in = reader(connection);
							out = writer(connection);
						}
						case "LOGIN" -> {
							if (!secured) {
								send(out, tag + " NO Not before STARTTLS");
								continue;
							}
							this.signedInOverTls = true;
							send(out, tag + " OK LOGIN completed");
						}
						case "EXAMINE", "SELECT" -> {
							send(out, "* FLAGS (\\Seen)");
							send(out, "* 0 EXISTS");
							send(out, "* 0 RECENT");
							send(out, "* OK [UIDVALIDITY 1] UIDs valid");
							send(out, tag + " OK [READ-ONLY] EXAMINE completed");
						}
						case "LOGOUT" -> {
							send(out, "* BYE Logging out");
							send(out, tag + " OK LOGOUT completed");
							return;
						}
						default -> send(out, tag + " OK " + command + " completed");
					}
				}
			} catch (IOException gone) {
				// The client hung up, e.g. after a certificate it didn't trust
			} finally {
				try {
					connection.close();
				} catch (IOException ignored) {
					// Closed already
				}
			}
		}

		private static BufferedReader reader(Socket connection) throws IOException {
			return new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
		}

		private static Writer writer(Socket connection) throws IOException {
			return new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.US_ASCII);
		}

		private static void send(Writer out, String line) throws IOException {
			out.write(line + "\r\n");
			out.flush();
		}

		@Override
		public void close() throws IOException {
			this.socket.close();
			this.connections.shutdownNow();
		}

	}

}
