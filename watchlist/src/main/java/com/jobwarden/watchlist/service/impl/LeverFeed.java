package com.jobwarden.watchlist.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.LOCATION;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.strings;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;
import static com.jobwarden.watchlist.service.impl.FeedJson.workType;

/**
 * Lever's job boards, in the US (api.lever.co) and in the EU (api.eu.lever.co): the answer is the list of jobs, each
 * with its description in parts.
 */
final class LeverFeed implements BoardFeed {

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/v0/postings/" + board + "?mode=json");
	}

	@Override
	public @Nullable String jobsField() {
		return null;
	}

	@Override
	public @Nullable Posting posting(JsonNode job) {
		String title = text(job, "text");
		String link = text(job, "hostedUrl");
		if (title == null || link == null) {
			return null;
		}
		JsonNode categories = job.path("categories");
		List<String> places = strings(categories.path("allLocations"));
		String location = places.isEmpty() ? text(categories, LOCATION) : String.join("; ", places);
		String country = text(job, "country");
		Employment employment = Employment.of(text(categories, "commitment"));
		long createdAt = job.path("createdAt").asLong(0);
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(location)
			.countries((country != null && country.length() == 2) ? Set.of(country.toUpperCase(Locale.ROOT)) : Set.of())
			.workType(workType(text(job, "workplaceType")))
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt((createdAt > 0) ? Instant.ofEpochMilli(createdAt) : null)
			.salary(leverSalary(job.path("salaryRange")))
			.description(leverDescription(job))
			.build();
	}

	/** Its plain intro, its lists (what you'll do, what you need...) and what it adds after. */
	private static @Nullable String leverDescription(JsonNode job) {
		StringBuilder text = new StringBuilder();
		String intro = text(job, "descriptionPlain");
		text.append((intro != null) ? intro : "");
		for (JsonNode list : job.path("lists").values()) {
			String heading = text(list, "text");
			String items = HtmlText.of(text(list, "content"));
			if (items != null) {
				text.append("\n\n").append((heading != null) ? heading + "\n" : "").append(items);
			}
		}
		String after = text(job, "additionalPlain");
		text.append((after != null) ? "\n\n" + after : "");
		return shortened(text.toString().strip());
	}

	/**
	 * {"currency": "EUR", "interval": "per-year-salary", "min": 60000, "max": 80000}: EUR 60,000–80,000 per year;
	 * {"currency": "USD", "interval": "per-hour-wage", "min": 23.75, "max": 28.5}: USD 23.75–28.50 per hour wage.
	 */
	private static @Nullable String leverSalary(JsonNode range) {
		BigDecimal min = amount(range.path("min"));
		BigDecimal max = amount(range.path("max"));
		if (min.signum() <= 0 && max.signum() <= 0) {
			return null;
		}
		String currency = range.path("currency").asString("");
		String interval = range.path("interval").asString("").replace("-salary", "").replace('-', ' ');
		String amount = (min.signum() > 0 && max.signum() > 0 && min.compareTo(max) != 0)
				? formatted(min) + "–" + formatted(max) : formatted(min.max(max));
		return (currency + " " + amount + " " + interval).strip();
	}

	/** The field's amount, a number or a number written as text; zero without one. */
	private static BigDecimal amount(JsonNode value) {
		if (value.isNumber()) {
			return value.decimalValue();
		}
		try {
			return value.isString() ? new BigDecimal(value.asString().strip()) : BigDecimal.ZERO;
		} catch (NumberFormatException _) {
			return BigDecimal.ZERO;
		}
	}

	/** With thousands separators, whatever the server's language, and cents only when it has them: 60,000 or 23.75. */
	private static String formatted(BigDecimal amount) {
		BigDecimal plain = amount.stripTrailingZeros();
		return (plain.scale() <= 0) ? String.format(Locale.ROOT, "%,d", plain.toBigInteger())
				: String.format(Locale.ROOT, "%,.2f", amount);
	}

}
