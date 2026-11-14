package com.jobwarden.web.config;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTests {

	private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

	@Test
	void anHtmlPageGetsEveryHeaderAndTheContentSecurityPolicy() throws Exception {
		MockHttpServletResponse response = answer(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");

		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
		assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
		assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
		assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER))
			.contains("script-src 'self'")
			.doesNotContain("unsafe-eval")
			.contains("object-src 'none'");
	}

	@Test
	void aPdfGetsNoContentSecurityPolicySoChromesViewerStillOpensIt() throws Exception {
		MockHttpServletResponse response = answer(MediaType.APPLICATION_PDF_VALUE);

		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER)).isNull();
	}

	@Test
	void jsonGetsNoContentSecurityPolicy() throws Exception {
		MockHttpServletResponse response = answer(MediaType.APPLICATION_JSON_VALUE);

		assertThat(response.getHeader(SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER)).isNull();
	}

	@Test
	void tellsHtmlFromOtherTypes() {
		assertThat(SecurityHeadersFilter.isHtml("text/html")).isTrue();
		assertThat(SecurityHeadersFilter.isHtml("TEXT/HTML; charset=utf-8")).isTrue();
		assertThat(SecurityHeadersFilter.isHtml("text/plain")).isFalse();
		assertThat(SecurityHeadersFilter.isHtml("not a type")).isFalse();
		assertThat(SecurityHeadersFilter.isHtml(null)).isFalse();
	}

	private MockHttpServletResponse answer(String contentType) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.filter.doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest request, HttpServletResponse response) {
				response.setContentType(contentType);
			}
		}));
		return response;
	}

}
