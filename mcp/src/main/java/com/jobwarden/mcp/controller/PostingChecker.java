package com.jobwarden.mcp.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.common.net.PublicAddresses;
import com.jobwarden.mcp.dto.PostingCheck;
import com.jobwarden.mcp.enums.Verdict;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Tells whether a posting is still up by fetching its page, as a browser would: a page that's gone (404, 410), a
 * redirect to the error page, an expired posting's search or the list the posting was in, a JobPosting whose
 * validThrough has passed, or words such as "no longer accepting applications" mean closed. Sites that block automated
 * visits (403, 429, LinkedIn's 999) can't tell: Claude can open those itself.
 * <p>
 * The links were saved from pages, which anyone could have written, so it only goes to the public internet, also after
 * a redirect ({@link PublicAddresses}).
 */
@Component
@CustomLog
class PostingChecker {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	/** More than any posting's page; the words that tell are near the top anyway. */
	private static final int MAX_BYTES = 3 * 1024 * 1024;

	private static final int MAX_REDIRECTS = 5;

	/** A browser's: some sites answer anything else with a page of their own. */
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/141.0 Safari/537.36";

	private static final Set<Integer> GONE = Set.of(404, 410);

	/** What sites answer automated visits with: LinkedIn uses 999. */
	private static final Set<Integer> BLOCKED = Set.of(401, 403, 429, 503, 999);

	/** What closed postings say, in the languages the app speaks; looked for in the page's text, in lower case. */
	private static final List<String> CLOSED_WORDS = List.of("no longer accepting applications", "no longer available",
			"this job has expired", "this job is closed", "this job is no longer", "this position has been filled",
			"position has been filled", "this position is closed", "this position is no longer", "posting has been closed",
			"this posting is closed", "this posting has expired", "this vacancy has expired", "this vacancy is closed",
			"applications are closed", "applications have closed", "this job listing has expired",
			"this role has been filled", "the job you are looking for is no longer", "job is no longer open",
			"nicht mehr verfügbar", "stelle ist bereits besetzt", "stellenanzeige ist abgelaufen",
			"bewerbungsfrist ist abgelaufen", "n'est plus disponible", "offre a expiré", "offre expirée",
			"poste a été pourvu", "ya no está disponible", "la oferta ha caducado", "oferta caducada",
			"oferta wygasła", "ogłoszenie wygasło", "oferta jest nieaktualna", "rekrutacja została zakończona",
			"já não está disponível", "não está mais disponível", "vaga encerrada", "vaga expirada", "anunțul a expirat",
			"anuntul a expirat", "nu mai este disponibil", "postul a fost ocupat", "anunț expirat");

	/** What's in the address sites send a taken-down posting to ({@link #toErrorPage}). */
	private static final List<String> TAKEN_DOWN_MARKS = List.of("error=true", "trk=expired_jd_redirect");

	private static final Pattern JSON_LD =Pattern.compile("<script[^>]+application/ld\\+json[^>]*>(.*?)</script>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern JOB_POSTING = Pattern.compile("\"@type\"\\s*:\\s*\\[?\\s*\"JobPosting\"");

	private static final Pattern VALID_THROUGH = Pattern.compile("\"validThrough\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})");

	/** Not text a reader sees: what's inside is left out before looking for words. */
	private static final Pattern HIDDEN = Pattern.compile("<(script|style|noscript|template|svg)\\b[^>]*>.*?</\\1>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern TAG = Pattern.compile("<[^>]*>");

	private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w-]+)", Pattern.CASE_INSENSITIVE);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		// Followed by hand, so each address is checked, and the last one known
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	private final Predicate<InetAddress> allowed;

	PostingChecker() {
		this(PublicAddresses::isPublic);
	}

	/**
	 * @param allowed which addresses it may fetch from: the tests' own server is on this machine
	 */
	PostingChecker(Predicate<InetAddress> allowed) {
		this.allowed = allowed;
	}

	PostingCheck check(String link) {
		URI start;
		try {
			start = URI.create(link.strip());
			requireAllowed(start);
		} catch (IllegalArgumentException | IOException notAllowed) {
			return new PostingCheck(link, Verdict.UNKNOWN, true, "Not a public web address: " + notAllowed.getMessage(),
					null, null, null);
		}
		try {
			return fetch(start);
		} catch (IOException failed) {
			log.debug("Couldn't check {}: {}", link, failed.getMessage());
			return new PostingCheck(link, Verdict.UNKNOWN, false, "The page couldn't be loaded: " + failed.getMessage(),
					null, null, null);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return new PostingCheck(link, Verdict.UNKNOWN, false, "The check was interrupted", null, null, null);
		}
	}

	private PostingCheck fetch(URI start) throws IOException, InterruptedException {
		URI current = start;
		for (int redirects = 0;; redirects++) {
			requireAllowed(current);
			HttpRequest request = HttpRequest.newBuilder(current)
				.timeout(TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
				.header("Accept-Language", "en,*;q=0.5")
				.GET()
				.build();
			HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				int status = response.statusCode();
				Optional<String> location = response.headers().firstValue("Location");
				if (status / 100 == 3 && location.isPresent()) {
					if (redirects >= MAX_REDIRECTS) {
						throw new IOException("More than " + MAX_REDIRECTS + " redirects");
					}
					current = current.resolve(location.get());
					continue;
				}
				String finalUrl = current.equals(start) ? null : current.toString();
				if (GONE.contains(status)) {
					return result(start, Verdict.CLOSED, true, "The page is gone (" + status + ")", status, finalUrl, null);
				}
				if (BLOCKED.contains(status)) {
					return result(start, Verdict.UNKNOWN, false,
							"The site doesn't let automated visits see the page (" + status + "): open it in a browser",
							status, finalUrl, null);
				}
				if (status / 100 != 2) {
					return result(start, Verdict.UNKNOWN, false, "The site answered " + status, status, finalUrl, null);
				}
				if (finalUrl != null && toErrorPage(current)) {
					return result(start, Verdict.CLOSED, true,
							"It redirects to where the site sends postings it took down: it's been taken down",
							status, finalUrl, current.getRawQuery());
				}
				if (finalUrl != null && wentUpFrom(start, current)) {
					return result(start, Verdict.CLOSED, false,
							"It redirects to " + current + ", away from the posting: it's likely been taken down", status,
							finalUrl, null);
				}
				byte[] bytes = body.readNBytes(MAX_BYTES);
				return read(start, new String(bytes, charsetOf(response)), status, finalUrl);
			}
		}
	}

	/** What the page says: its job data first, then its words. */
	private static PostingCheck read(URI start, String html, int status, @Nullable String finalUrl) {
		boolean hasJobPosting = false;
		Matcher script = JSON_LD.matcher(html);
		while (script.find()) {
			String data = script.group(1);
			if (JOB_POSTING.matcher(data).find()) {
				hasJobPosting = true;
				Matcher validThrough = VALID_THROUGH.matcher(data);
				if (validThrough.find() && LocalDate.parse(validThrough.group(1)).isBefore(LocalDate.now())) {
					return result(start, Verdict.CLOSED, true, "Its job data says it ran until " + validThrough.group(1),
							status, finalUrl, "\"validThrough\": \"" + validThrough.group(1) + "\"");
				}
			}
		}
		String text = textOf(html);
		for (String words : CLOSED_WORDS) {
			int at = text.indexOf(words);
			if (at >= 0) {
				return result(start, Verdict.CLOSED, true, "The page says the posting is closed", status, finalUrl,
						text.substring(Math.max(0, at - 60), Math.min(text.length(), at + words.length() + 60)).strip());
			}
		}
		return hasJobPosting
				? result(start, Verdict.OPEN, true, "The page has the posting's job data, still valid", status, finalUrl, null)
				: result(start, Verdict.OPEN, false,
						"The page loads and doesn't say the posting is closed, but has no job data to be sure", status,
						finalUrl, null);
	}

	/**
	 * Where sites send a posting they took down: Greenhouse to the board, with ?error=true, LinkedIn to a job search,
	 * with ?trk=expired_jd_redirect.
	 */
	private static boolean toErrorPage(URI end) {
		String query = end.getRawQuery();
		return query != null && TAKEN_DOWN_MARKS.stream().anyMatch(query::contains);
	}

	/**
	 * Whether the redirect went to a page above the posting, e.g. from a job at /acme/jobs/123 to the board at /acme.
	 */
	private static boolean wentUpFrom(URI start, URI end) {
		if (!end.getHost().equalsIgnoreCase(start.getHost())) {
			return false;
		}
		String from = trimmedPath(start);
		String to = trimmedPath(end);
		return !from.equals(to) && (to.isEmpty() || from.startsWith(to + "/"));
	}

	private static String trimmedPath(URI uri) {
		String path = (uri.getRawPath() == null) ? "" : uri.getRawPath();
		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
	}

	/** The words a reader sees, in lower case, with one kind of apostrophe and single spaces. */
	private static String textOf(String html) {
		String text = TAG.matcher(HIDDEN.matcher(html).replaceAll(" ")).replaceAll(" ")
			.replace("&nbsp;", " ")
			.replace("&#39;", "'")
			.replace("&#x27;", "'")
			.replace("&rsquo;", "'")
			.replace("&amp;", "&")
			.replace('’', '\'');
		return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static Charset charsetOf(HttpResponse<?> response) {
		return response.headers()
			.firstValue("Content-Type")
			.map(CHARSET::matcher)
			.filter(Matcher::find)
			.map(matcher -> {
				try {
					return Charset.forName(matcher.group(1));
				} catch (IllegalArgumentException unknown) {
					return StandardCharsets.UTF_8;
				}
			})
			.orElse(StandardCharsets.UTF_8);
	}

	private void requireAllowed(URI address) throws IOException {
		if ((!"https".equals(address.getScheme()) && !"http".equals(address.getScheme())) || address.getHost() == null) {
			throw new IOException("not a web address");
		}
		for (InetAddress resolved : InetAddress.getAllByName(address.getHost())) {
			if (!this.allowed.test(resolved)) {
				throw new IOException("not on the public internet: " + resolved.getHostAddress());
			}
		}
	}

	private static PostingCheck result(URI start, Verdict verdict, boolean certain, String reason, @Nullable Integer status,
			@Nullable String finalUrl, @Nullable String evidence) {
		return new PostingCheck(start.toString(), verdict, certain, reason, status, finalUrl, evidence);
	}

}
