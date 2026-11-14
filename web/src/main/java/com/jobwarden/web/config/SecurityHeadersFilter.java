package com.jobwarden.web.config;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jspecify.annotations.Nullable;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The browser's own defences, on every response: content types taken as sent, no framing by other sites, no referrer
 * sent to the sites the pages link to (job boards learn nothing of the server), no access to the camera and the like,
 * and a window of its own.
 * <p>
 * HTML pages also get a Content Security Policy: scripts, styles, images, fonts and connections from this server only,
 * so text that slipped into a page, e.g. from a job posting, can't run as a script. It's why the templates have no
 * inline scripts or event handlers. Inline styles stay allowed: some elements are sized by the server ({@code style}
 * attributes) and HTMX adds a {@code <style>} of its own. Only HTML gets it, as a PDF given one would no longer open
 * in Chrome's viewer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SecurityHeadersFilter extends OncePerRequestFilter {

	static final String CONTENT_SECURITY_POLICY = String.join("; ",
			"default-src 'self'",
			"script-src 'self'",
			"style-src 'self' 'unsafe-inline'",
			"img-src 'self' data: blob:",
			"font-src 'self' data:",
			"connect-src 'self'",
			"frame-src 'self'",
			"frame-ancestors 'self'",
			"form-action 'self'",
			"base-uri 'self'",
			"object-src 'none'");

	static final Map<String, String> HEADERS = Map.of(
			"X-Content-Type-Options", "nosniff",
			"X-Frame-Options", "SAMEORIGIN",
			"Referrer-Policy", "same-origin",
			"Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
			"Cross-Origin-Opener-Policy", "same-origin");

	static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HEADERS.forEach(response::setHeader);
		chain.doFilter(request, new HtmlPolicyResponse(response));
	}

	/**
	 * Error pages are HTML too.
	 */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	/**
	 * Adds the Content Security Policy once the response says it's HTML, which the view decides after the filter ran.
	 */
	private static final class HtmlPolicyResponse extends HttpServletResponseWrapper {

		HtmlPolicyResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void setContentType(@Nullable String type) {
			super.setContentType(type);
			applyPolicy(type);
		}

		@Override
		public void setHeader(String name, @Nullable String value) {
			super.setHeader(name, value);
			if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
				applyPolicy(value);
			}
		}

		@Override
		public void addHeader(String name, @Nullable String value) {
			super.addHeader(name, value);
			if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
				applyPolicy(value);
			}
		}

		private void applyPolicy(@Nullable String type) {
			if (isHtml(type) && !isCommitted()) {
				super.setHeader(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY);
			}
		}

	}

	static boolean isHtml(@Nullable String type) {
		if (type == null || type.isBlank()) {
			return false;
		}
		try {
			return MediaType.TEXT_HTML.isCompatibleWith(MediaType.parseMediaType(type));
		} catch (InvalidMediaTypeException notAType) {
			return false;
		}
	}

}
