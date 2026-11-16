package com.jobwarden.web.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.jobwarden.web.constants.ControllerConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a request addressed to a host name that could point anywhere: the defence against DNS rebinding. A page on
 * attacker.example, whose name its owner then points at 127.0.0.1, reaches the server as the same site, so neither the
 * extension's API's CORS nor the MCP server's Origin check sees anything wrong; its Host header still says
 * attacker.example.
 * <p>
 * Allowed: IP addresses; {@code localhost} and {@code *.localhost}; names without a dot (a computer on the LAN, a Docker
 * service); names under {@code .local}, {@code .lan}, {@code .home.arpa} and {@code .internal}, a home network's; this
 * computer's own name; and the names in {@code jobwarden.allowed-hosts}, e.g. a domain a reverse proxy serves it as.
 * The calendar feed answers any host: Google fetches it through a reverse proxy, and its token is what protects it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@CustomLog
class HostCheckFilter extends OncePerRequestFilter {

	private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

	private static final List<String> LOCAL_SUFFIXES = List.of(".localhost", ".local", ".lan", ".home.arpa", ".internal");

	private static final String FEED = ControllerConstants.CALENDAR + "/feed.ics";

	private final Set<String> allowed = new HashSet<>();

	/**
	 * @param allowedHosts host names to allow besides the local ones, e.g. jobwarden.example.com
	 */
	HostCheckFilter(@Value("${jobwarden.allowed-hosts:}") List<String> allowedHosts) {
		allowedHosts.stream().map(HostCheckFilter::normalized).filter(host -> !host.isEmpty()).forEach(this.allowed::add);
		try {
			String own = normalized(InetAddress.getLocalHost().getHostName());
			this.allowed.add(own);
			this.allowed.add(own.split("\\.", 2)[0]);
		} catch (UnknownHostException _) {
			// No name of its own to allow: the local and listed ones still are
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String host = normalized(request.getServerName());
		if (isAllowed(host) || FEED.equals(request.getRequestURI().substring(request.getContextPath().length()))) {
			chain.doFilter(request, response);
			return;
		}
		log.warn("Refused a request for host {}: add it to jobwarden.allowed-hosts if it's this server's", host);
		response.sendError(HttpStatus.FORBIDDEN.value(),
				"This server doesn't answer to " + host + ": add it to jobwarden.allowed-hosts if it should");
	}

	boolean isAllowed(String host) {
		return host.isEmpty() || isIpAddress(host) || host.equals("localhost") || !host.contains(".")
				|| LOCAL_SUFFIXES.stream().anyMatch(host::endsWith) || this.allowed.contains(host);
	}

	private static boolean isIpAddress(String host) {
		return IPV4.matcher(host).matches() || host.contains(":");
	}

	/** Lower case, without a trailing dot or an IPv6 address's brackets. */
	static String normalized(String host) {
		String name = host.strip().toLowerCase(Locale.ROOT);
		if (name.startsWith("[") && name.endsWith("]")) {
			name = name.substring(1, name.length() - 1);
		}
		return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
	}

}
