package com.jobwarden.web.config;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HostCheckFilterTests {

	private final HostCheckFilter filter = new HostCheckFilter(List.of("jobwarden.example.com"));

	@Test
	void answersTheLocalNamesAndAddresses() {
		assertThat(List.of("localhost", "app.localhost", "127.0.0.1", "192.168.1.20", "::1", "[::1]", "nas", "jobwarden",
				"nas.local", "box.lan", "server.home.arpa", "host.docker.internal", "LOCALHOST", "localhost."))
			.allSatisfy(host -> assertThat(this.filter.isAllowed(HostCheckFilter.normalized(host))).as(host).isTrue());
	}

	@Test
	void answersTheNamesItsGiven() {
		assertThat(this.filter.isAllowed("jobwarden.example.com")).isTrue();
	}

	@Test
	void refusesAnyOtherDomainAsARebindingPageWouldUse() throws Exception {
		assertThat(this.filter.isAllowed("attacker.example")).isFalse();
		assertThat(this.filter.isAllowed("127.0.0.1.nip.io")).isFalse();

		MockHttpServletResponse response = answer("attacker.example", "/applications");

		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void theCalendarFeedAnswersAnyHostAsItsTokenProtectsIt() throws Exception {
		assertThat(answer("calendar.example.org", "/calendar/feed.ics").getStatus()).isEqualTo(200);
	}

	private MockHttpServletResponse answer(String host, String path) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setServerName(host);
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

}
