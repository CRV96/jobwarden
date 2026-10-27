package com.jobwarden.search;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;


import org.springframework.stereotype.Service;

/**
 * Turns {@link SearchCriteria} into a Boolean query for each {@link Platform}. Alternatives are joined with OR in
 * parentheses, terms of more than one word are quoted, and the groups are joined with spaces, which search engines
 * read as AND, or with an explicit AND for LinkedIn. Quotes and parentheses inside a term are dropped, so a term
 * can't break the query's structure.
 */
@Service
public class QueryGenerator {

	private static final String REDDIT_SITES = "(site:reddit.com/r/forhire OR site:reddit.com/r/remotejs OR site:reddit.com/r/hiring)";

	private static final String HIRING_PHRASES = "(\"hiring\" OR \"we're hiring\" OR \"now hiring\")";

	private static final String REMOTE_BOARD_SITES = "(site:remoteok.com OR site:weworkremotely.com OR site:remotive.com)";

	public GeneratedQueries generate(SearchCriteria criteria) {
		LimitedQuery google = google(criteria);
		List<GeneratedQuery> queries = List.of(onGoogle(Platform.GOOGLE, google.query(), criteria.recency()),
				onLinkedIn(linkedIn(criteria), criteria.recency()),
				onGoogle(Platform.REDDIT, reddit(criteria), criteria.recency()), onX(x(criteria)),
				onGoogle(Platform.REMOTE_BOARDS, remoteBoards(criteria), criteria.recency()));
		return new GeneratedQueries(queries, google.words(), google.truncated());
	}

	/**
	 * The sites, then what to look for, at most {@link GeneratedQueries#GOOGLE_WORD_LIMIT} words: Google ignores the
	 * rest. A group of alternatives that doesn't fit loses its last terms until it does; the groups after it are left
	 * out.
	 */
	private static LimitedQuery google(SearchCriteria criteria) {
		List<Group> groups = List.of(Group.anyOf(criteria.sites().stream().map(QueryGenerator::site).toList()),
				Group.anyOf(quoted(criteria.titles())), Group.anyOf(quoted(criteria.skills())),
				Group.anyOf(quoted(terms(criteria.employmentTypes()))), Group.anyOf(quoted(terms(criteria.workTypes()))),
				Group.anyOf(quoted(criteria.locations())), Group.allOf(excluded(criteria.excludes(), "-")));

		List<String> parts = new ArrayList<>();
		int words = 0;
		for (Group group : groups) {
			if (group.terms().isEmpty()) {
				continue;
			}
			if (words + group.words() <= GeneratedQueries.GOOGLE_WORD_LIMIT) {
				parts.add(group.text());
				words += group.words();
				continue;
			}
			Group fitting = group;
			while (fitting.canShrink() && words + fitting.words() > GeneratedQueries.GOOGLE_WORD_LIMIT) {
				fitting = fitting.withoutLastTerm();
			}
			if (words + fitting.words() <= GeneratedQueries.GOOGLE_WORD_LIMIT) {
				parts.add(fitting.text());
				words += fitting.words();
			}
			return new LimitedQuery(String.join(" ", parts), words, true);
		}
		return new LimitedQuery(String.join(" ", parts), words, false);
	}

	/** LinkedIn's job search reads AND, OR and NOT, in capitals. */
	private static String linkedIn(SearchCriteria criteria) {
		String wanted = String.join(" AND ", nonEmpty(anyOf(criteria.titles()), anyOf(criteria.skills()),
				anyOf(terms(criteria.employmentTypes())), anyOf(terms(criteria.workTypes())), anyOf(criteria.locations())));
		return joined(wanted, String.join(" ", excluded(criteria.excludes(), "NOT ")));
	}

	private static String reddit(SearchCriteria criteria) {
		return joined(REDDIT_SITES, anyOf(criteria.titles()), anyOf(terms(criteria.workTypes())),
				anyOf(criteria.locations()), String.join(" ", excluded(criteria.excludes(), "-")));
	}

	private static String x(SearchCriteria criteria) {
		return joined(HIRING_PHRASES, anyOf(criteria.titles()), anyOf(terms(criteria.workTypes())),
				anyOf(criteria.locations()), String.join(" ", excluded(criteria.excludes(), "-")), "-filter:retweets");
	}

	private static String remoteBoards(SearchCriteria criteria) {
		return joined(REMOTE_BOARD_SITES, anyOf(criteria.titles()), anyOf(terms(criteria.employmentTypes())),
				anyOf(criteria.locations()), String.join(" ", excluded(criteria.excludes(), "-")));
	}

	/** Newest first (sbd:1) when the search asks for recent postings. */
	private static GeneratedQuery onGoogle(Platform platform, String query, Recency recency) {
		String period = recency.googlePeriod();
		return generated(platform, query,
				"https://www.google.com/search?q=" + encoded(query) + (period == null ? "" : "&tbs=qdr:" + period + ",sbd:1"));
	}

	private static GeneratedQuery onLinkedIn(String query, Recency recency) {
		Duration age = recency.age();
		return generated(Platform.LINKEDIN, query, "https://www.linkedin.com/jobs/search/?keywords=" + encoded(query)
				+ (age == null ? "" : "&f_TPR=r" + age.toSeconds()));
	}

	/** The latest posts (f=live): X has no period to ask for. */
	private static GeneratedQuery onX(String query) {
		return generated(Platform.X, query, "https://x.com/search?q=" + encoded(query) + "&f=live");
	}

	private static GeneratedQuery generated(Platform platform, String query, String searchUrl) {
		return new GeneratedQuery(platform, query, query.isEmpty() ? null : searchUrl);
	}

	/** The term without what would change the query's structure: quotes and parentheses. */
	private static String plain(String term) {
		return term.replaceAll("[\"()]", "").strip();
	}

	private static List<String> quoted(Collection<String> terms) {
		return terms.stream().map(QueryGenerator::plain).filter(term -> !term.isEmpty())
			.map(term -> term.contains(" ") ? '"' + term + '"' : term)
			.toList();
	}

	/** A domain as Google's site: wants it, without the scheme and the trailing slash. */
	private static String site(String domain) {
		return "site:" + plain(domain).replaceFirst("^https?://", "").replaceFirst("/+$", "");
	}

	private static List<String> excluded(Collection<String> terms, String operator) {
		return quoted(terms).stream().map(term -> operator + term).toList();
	}

	private static String anyOf(Collection<String> terms) {
		return Group.anyOf(quoted(terms)).text();
	}

	private static List<String> terms(Collection<? extends Enum<?>> values) {
		return values.stream().map(value -> switch (value) {
			case WorkType workType -> workType.term();
			case EmploymentType employmentType -> employmentType.term();
			default -> throw new IllegalArgumentException("Not a search term: " + value);
		}).toList();
	}

	private static List<String> nonEmpty(String... parts) {
		return Stream.of(parts).filter(part -> !part.isEmpty()).toList();
	}

	private static String joined(String... parts) {
		return String.join(" ", nonEmpty(parts));
	}

	private static String encoded(String query) {
		return URLEncoder.encode(query, StandardCharsets.UTF_8);
	}

	private static int wordsIn(String text) {
		String trimmed = text.strip();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

	/**
	 * Terms that make one part of a query: alternatives (OR) or terms that must all apply, such as the excluded ones.
	 */
	private record Group(List<String> terms, boolean alternatives) {

		static Group anyOf(List<String> terms) {
			return new Group(terms, true);
		}

		static Group allOf(List<String> terms) {
			return new Group(terms, false);
		}

		String text() {
			if (this.terms.isEmpty()) {
				return "";
			}
			if (!this.alternatives) {
				return String.join(" ", this.terms);
			}
			return (this.terms.size() == 1) ? this.terms.getFirst() : "(" + String.join(" OR ", this.terms) + ")";
		}

		int words() {
			return wordsIn(text());
		}

		boolean canShrink() {
			return this.alternatives && this.terms.size() > 1;
		}

		Group withoutLastTerm() {
			return new Group(this.terms.subList(0, this.terms.size() - 1), this.alternatives);
		}

	}

	private record LimitedQuery(String query, int words, boolean truncated) {
	}

}
