package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.jobwarden.watchlist.enums.Ats;
import org.jspecify.annotations.Nullable;

/**
 * A company's job board: its system, and its name there, in lower case, as the boards' feeds take it.
 *
 * @param name the board's name, e.g. acme in jobs.lever.co/acme
 */
record Board(Ats ats, String name) {

	/** What a board's name may be: letters, digits, dots, dashes, underscores; Ashby's spaces too. */
	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9._ -]{0,99}");

	/**
	 * What the name of a board that is a host of its own may be (Recruitee's, Personio's): one label of a domain name,
	 * so the address stays on the system's domain.
	 */
	private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

	/** Recruitee's own hosts, which are no company's board. */
	private static final Set<String> RECRUITEE_HOSTS = Set.of("app", "api", "blog", "docs", "help", "status", "support");

	/** Where SmartRecruiters' links to apply or refer say the company: oneclick-ui/company/Acme/publication/... */
	private static final Set<String> SMARTRECRUITERS_PAGES = Set.of("oneclick-ui", "external-referrals");

	private static final String RECRUITEE_DOMAIN = ".recruitee.com";

	private static final List<String> PERSONIO_DOMAINS = List.of(".jobs.personio.de", ".jobs.personio.com");

	/**
	 * The board a link is on: the board's own page or one of its jobs, as people share them, or its feed.
	 * <ul>
	 * <li>Greenhouse: boards.greenhouse.io/acme, job-boards.greenhouse.io/acme/jobs/123,
	 * boards.greenhouse.io/embed/job_board?for=acme, boards-api.greenhouse.io/v1/boards/acme/jobs</li>
	 * <li>Lever: jobs.lever.co/acme/1a2b..., jobs.eu.lever.co/acme, api.lever.co/v0/postings/acme</li>
	 * <li>Ashby: jobs.ashbyhq.com/acme/1a2b..., api.ashbyhq.com/posting-api/job-board/acme</li>
	 * <li>SmartRecruiters: jobs.smartrecruiters.com/Acme/7440001..., careers.smartrecruiters.com/Acme,
	 * jobs.smartrecruiters.com/oneclick-ui/company/Acme/publication/..., api.smartrecruiters.com/v1/companies/acme</li>
	 * <li>Workable: apply.workable.com/acme/j/1A2B3C/, apply.workable.com/api/v1/widget/accounts/acme (not
	 * apply.workable.com/j/1A2B3C, which doesn't say whose it is)</li>
	 * <li>Recruitee: acme.recruitee.com/o/backend-developer (not a company's own domain, which says nothing of
	 * Recruitee)</li>
	 * <li>Personio: acme.jobs.personio.de/job/123, acme.jobs.personio.com</li>
	 * </ul>
	 *
	 * @param link with or without https://
	 * @return empty when it's on none of them, or names no board
	 */
	static Optional<Board> of(@Nullable String link) {
		if (link == null || link.isBlank()) {
			return Optional.empty();
		}
		String address = link.strip();
		if (!address.contains("://")) {
			address = "https://" + address;
		}
		URI uri;
		try {
			uri = URI.create(address);
		} catch (IllegalArgumentException _) {
			// Not an address at all
			return Optional.empty();
		}
		if (uri.getHost() == null || !List.of("http", "https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))) {
			return Optional.empty();
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		host = host.startsWith("www.") ? host.substring("www.".length()) : host;
		List<String> path = Arrays.stream(String.valueOf(uri.getRawPath()).split("/"))
			.filter(segment -> !segment.isEmpty())
			.map(segment -> URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8))
			.toList();
		return switch (host) {
			case "boards.greenhouse.io", "job-boards.greenhouse.io" -> (!path.isEmpty() && "embed".equals(path.getFirst()))
					? named(Ats.GREENHOUSE, query(uri, "for")) : named(Ats.GREENHOUSE, segment(path, 0));
			case "boards-api.greenhouse.io" -> segment(path, 1).equals("boards") ? named(Ats.GREENHOUSE, segment(path, 2))
					: Optional.empty();
			case "jobs.lever.co" -> named(Ats.LEVER, segment(path, 0));
			case "jobs.eu.lever.co" -> named(Ats.LEVER_EU, segment(path, 0));
			case "api.lever.co" -> segment(path, 1).equals("postings") ? named(Ats.LEVER, segment(path, 2)) : Optional.empty();
			case "api.eu.lever.co" -> segment(path, 1).equals("postings") ? named(Ats.LEVER_EU, segment(path, 2))
					: Optional.empty();
			case "jobs.ashbyhq.com" -> named(Ats.ASHBY, segment(path, 0));
			case "api.ashbyhq.com" -> segment(path, 1).equals("job-board") ? named(Ats.ASHBY, segment(path, 2))
					: Optional.empty();
			case "jobs.smartrecruiters.com" -> SMARTRECRUITERS_PAGES.contains(segment(path, 0))
					? (segment(path, 1).equals("company") ? named(Ats.SMARTRECRUITERS, segment(path, 2)) : Optional.empty())
					: named(Ats.SMARTRECRUITERS, segment(path, 0));
			case "careers.smartrecruiters.com" -> named(Ats.SMARTRECRUITERS, segment(path, 0));
			case "api.smartrecruiters.com" -> segment(path, 1).equals("companies")
					? named(Ats.SMARTRECRUITERS, segment(path, 2)) : Optional.empty();
			case "apply.workable.com" -> workable(path);
			default -> ownHost(host);
		};
	}

	/**
	 * apply.workable.com/acme/..., or its feed, apply.workable.com/api/v1/widget/accounts/acme; the short links to a
	 * job, apply.workable.com/j/1A2B3C, don't say whose board it's on.
	 */
	private static Optional<Board> workable(List<String> path) {
		String first = segment(path, 0);
		if (first.equals("api")) {
			int accounts = path.indexOf("accounts");
			return (accounts > 0) ? named(Ats.WORKABLE, segment(path, accounts + 1)) : Optional.empty();
		}
		return first.equals("j") ? Optional.empty() : named(Ats.WORKABLE, first);
	}

	/**
	 * The boards that are hosts of their own: acme.recruitee.com, acme.jobs.personio.de (or .com, the same boards).
	 */
	private static Optional<Board> ownHost(String host) {
		if (host.endsWith(RECRUITEE_DOMAIN)) {
			String label = host.substring(0, host.length() - RECRUITEE_DOMAIN.length());
			return RECRUITEE_HOSTS.contains(label) ? Optional.empty() : hostNamed(Ats.RECRUITEE, label);
		}
		return PERSONIO_DOMAINS.stream()
			.filter(host::endsWith)
			.findFirst()
			.flatMap(domain -> hostNamed(Ats.PERSONIO, host.substring(0, host.length() - domain.length())));
	}

	/**
	 * @return where people see the board, e.g. https://jobs.lever.co/acme
	 */
	String url() {
		return this.ats.boardUrl(this.name);
	}

	/**
	 * @return the company's name as the board's own may say it: acme-corp is Acme Corp
	 */
	String companyName() {
		StringBuilder name = new StringBuilder();
		for (String word : this.name.split("[-_. ]+")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return name.isEmpty() ? this.name : name.toString();
	}

	private static Optional<Board> named(Ats ats, String name) {
		String lower = name.strip().toLowerCase(Locale.ROOT);
		return VALID_NAME.matcher(lower).matches() ? Optional.of(new Board(ats, lower)) : Optional.empty();
	}

	/**
	 * @param label the host's part before the system's domain: one label, never a.b
	 */
	private static Optional<Board> hostNamed(Ats ats, String label) {
		return isHostLabel(label) ? Optional.of(new Board(ats, label)) : Optional.empty();
	}

	/**
	 * @return whether the name can be the first label of a host: what a board on Recruitee or Personio has to be
	 */
	static boolean isHostLabel(String name) {
		return HOST_LABEL.matcher(name).matches();
	}

	private static String segment(List<String> path, int index) {
		return (index < path.size()) ? path.get(index) : "";
	}

	private static String query(URI uri, String parameter) {
		String query = uri.getRawQuery();
		if (query == null) {
			return "";
		}
		for (String pair : query.split("&")) {
			int equals = pair.indexOf('=');
			if (equals > 0 && pair.substring(0, equals).equals(parameter)) {
				return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
			}
		}
		return "";
	}

}
