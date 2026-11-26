package com.jobwarden.inbox.service.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MailServerTests {

	@ParameterizedTest
	@ValueSource(strings = { "127.0.0.1", "localhost", "LOCALHOST", "::1", "[::1]" })
	void knowsTheComputersOwnAddress(String host) {
		MailServer server = server(host);

		assertThat(server.isLocal()).isTrue();
		assertThat(server.isLoopback()).isTrue();
	}

	/** From a container, its name for the computer: Bridge there signs its own certificate too. */
	@ParameterizedTest
	@ValueSource(strings = { "host.docker.internal", "host.orb.internal", "host.containers.internal" })
	void knowsTheComputerAsAContainerNamesIt(String host) {
		MailServer server = server(host);

		assertThat(server.isLocal()).isTrue();
		assertThat(server.isLoopback()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "imap.gmail.com", "127.0.0.2", "docker.internal" })
	void leavesOtherServersToBeCheckedAsUsual(String host) {
		assertThat(server(host).isLocal()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void movesToAnotherHostWithTheRest(boolean starttls) {
		MailServer server = new MailServer("127.0.0.1", 1143, "robert@proton.me", "bridge", "Labels/Jobs", starttls,
				"AB:CD");

		assertThat(server.withHost("host.docker.internal")).isEqualTo(new MailServer("host.docker.internal", 1143,
				"robert@proton.me", "bridge", "Labels/Jobs", starttls, "AB:CD"));
	}

	private static MailServer server(String host) {
		return new MailServer(host, 1143, "robert@proton.me", "bridge", "INBOX");
	}

}
