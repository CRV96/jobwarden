package com.jobwarden.web.config;

import java.io.IOException;
import java.util.regex.Pattern;

import com.jobwarden.common.logging.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request the server answers an id: the pages, the extension's API and the MCP server alike. Every line
 * logged while answering it carries the id (the {@code requestId} MDC key, which {@code logging.pattern.correlation}
 * prints), and the {@code X-Request-Id} response header gives it to the caller, so a failure someone reports leads
 * straight to its log lines.
 * <p>
 * An id the caller sends, e.g. from a proxy in front, is kept when it's a plain token; anything else, which could forge
 * log lines, is replaced. The error page rendered for a failed request keeps the request's id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Request-Id";

	static final String MDC_KEY = LogContext.ID;

	/** Where the id waits for the error page's dispatch, which has no MDC of the request's own. */
	private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

	private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = requestId(request);
		request.setAttribute(ATTRIBUTE, id);
		if (!response.containsHeader(HEADER)) {
			response.setHeader(HEADER, id);
		}
		try (MDC.MDCCloseable _ = MDC.putCloseable(MDC_KEY, id)) {
			chain.doFilter(request, response);
		}
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	private static String requestId(HttpServletRequest request) {
		if (request.getAttribute(ATTRIBUTE) instanceof String known) {
			return known;
		}
		return accepted(request.getHeader(HEADER));
	}

	/**
	 * @return the caller's id when it's a plain token of up to 64 characters, else a new one: 16 hexadecimal digits
	 */
	static String accepted(@Nullable String sent) {
		if (sent != null && ACCEPTED.matcher(sent).matches()) {
			return sent;
		}
		return LogContext.newId();
	}

}
