package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.LOCATION;
import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;

/**
 * Recruitee's job boards, each a host of its own (acme.recruitee.com/api/offers/): every published job at once, with
 * its description, its requirements and, when the company shows it, its pay. A job's link is on the company's careers
 * site, which may be a domain of the company's own.
 */
final class RecruiteeFeed implements BoardFeed {

	private static final String COUNTRY_CODE = "country_code";

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/api/offers/");
	}

	@Override
	public String jobsField() {
		return "offers";
	}

	@Override
	public @Nullable Posting posting(JsonNode job, Board board) {
		String title = text(job, "title");
		String link = text(job, "careers_url");
		String status = text(job, "status");
		if (title == null || link == null || (status != null && !status.equals("published"))) {
			return null;
		}
		String places = job.path("locations").values().stream()
			.map(RecruiteeFeed::place)
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.joining(Posting.PLACES));
		Set<String> countries = new LinkedHashSet<>();
		Stream.concat(Stream.of(job), job.path("locations").values().stream())
			.map(place -> text(place, COUNTRY_CODE))
			.filter(code -> code != null && code.length() == 2)
			.forEach(code -> countries.add(code.toUpperCase(Locale.ROOT)));
		Employment employment = Employment.of(text(job, "employment_type_code"));
		String published = text(job, "published_at");
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(places.isEmpty() ? text(job, LOCATION) : places)
			.countries(countries)
			.workType(workType(job))
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt(instant((published != null) ? published : text(job, "created_at")))
			.salary(salary(job.path("salary")))
			.description(description(job))
			.company(text(job, "company_name"))
			.build();
	}

	/**
	 * @return e.g. Utrecht, Netherlands; {@code null} when it names neither
	 */
	private static @Nullable String place(JsonNode place) {
		String named = Stream.of(text(place, "city"), text(place, "country"))
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));
		return named.isEmpty() ? null : named;
	}

	/**
	 * A job may say it's remote, hybrid and on site all at once, for the candidate to choose: remote comes first, as
	 * it's the one a remote search looks for, then hybrid.
	 */
	private static @Nullable WorkType workType(JsonNode job) {
		if (job.path("remote").asBoolean(false)) {
			return WorkType.REMOTE;
		}
		if (job.path("hybrid").asBoolean(false)) {
			return WorkType.HYBRID;
		}
		return job.path("on_site").asBoolean(false) ? WorkType.ONSITE : null;
	}

	/**
	 * {"min": "100000", "max": "120000", "period": "year", "currency": "EUR"}: EUR 100,000–120,000 per year.
	 */
	private static @Nullable String salary(JsonNode salary) {
		String period = text(salary, "period");
		return SalaryRange.of(Objects.requireNonNullElse(text(salary, "currency"), ""), salary.path("min"),
				salary.path("max"), (period != null) ? "per " + period.replace('_', ' ') : "");
	}

	/** What the job is, then what it asks for. */
	private static @Nullable String description(JsonNode job) {
		String text = Stream.of(HtmlText.of(text(job, "description")), HtmlText.of(text(job, "requirements")))
			.filter(Objects::nonNull)
			.collect(Collectors.joining("\n\n"));
		return shortened(text);
	}

}
