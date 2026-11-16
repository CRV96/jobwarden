package com.jobwarden.web.config;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTests {

	private final RequestIdFilter filter = new RequestIdFilter();

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/applications");

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@Test
	void givesTheRequestAnIdInTheLogsAndTheResponse() throws Exception {
		AtomicReference<String> logged = new AtomicReference<>();

		this.filter.doFilter(this.request, this.response, new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest request, HttpServletResponse response) {
				logged.set(MDC.get(RequestIdFilter.MDC_KEY));
			}
		}));

		String id = this.response.getHeader(RequestIdFilter.HEADER);
		assertThat(id).matches("[0-9a-f]{16}");
		assertThat(logged).hasValue(id);
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void keepsAPlainIdTheCallerSent() throws Exception {
		this.request.addHeader(RequestIdFilter.HEADER, "proxy-42.a_b");

		this.filter.doFilter(this.request, this.response, new MockFilterChain());

		assertThat(this.response.getHeader(RequestIdFilter.HEADER)).isEqualTo("proxy-42.a_b");
	}

	@Test
	void replacesAnIdThatCouldForgeLogLines() {
		assertThat(RequestIdFilter.accepted("forged\n2026-11-14 ERROR")).matches("[0-9a-f]{16}");
		assertThat(RequestIdFilter.accepted("x".repeat(65))).matches("[0-9a-f]{16}");
		assertThat(RequestIdFilter.accepted("")).matches("[0-9a-f]{16}");
		assertThat(RequestIdFilter.accepted(null)).matches("[0-9a-f]{16}");
	}

	@Test
	void theErrorPageKeepsTheRequestsId() throws Exception {
		this.filter.doFilter(this.request, this.response, new MockFilterChain());
		String id = this.response.getHeader(RequestIdFilter.HEADER);

		this.request.setDispatcherType(DispatcherType.ERROR);
		MockHttpServletResponse errorPage = new MockHttpServletResponse();
		this.filter.doFilter(this.request, errorPage, new MockFilterChain());

		assertThat(errorPage.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
	}

}
