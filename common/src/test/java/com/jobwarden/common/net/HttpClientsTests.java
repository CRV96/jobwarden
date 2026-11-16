package com.jobwarden.common.net;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientsTests {

	@Test
	void anApisClientFollowsRedirectsAPagesDoesnt() {
		assertThat(HttpClients.followingRedirects(Duration.ofSeconds(3)).followRedirects())
			.isEqualTo(HttpClient.Redirect.NORMAL);
		assertThat(HttpClients.notFollowingRedirects(Duration.ofSeconds(3)).followRedirects())
			.isEqualTo(HttpClient.Redirect.NEVER);
	}

	@Test
	void eachHasItsConnectTimeoutAndThreadsOfItsOwn() {
		HttpClient client = HttpClients.followingRedirects(Duration.ofSeconds(7));

		assertThat(client.connectTimeout()).contains(Duration.ofSeconds(7));
		assertThat(client.executor()).isPresent();
	}

	@Test
	void namesJobWardenAndWhereItsFrom() {
		assertThat(HttpClients.userAgent("1.2.0")).isEqualTo("JobWarden/1.2.0 (+https://github.com/CRV96/jobwarden)");
	}

}
