package com.jobwarden.watchlist.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
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

	/** More than any salary has, before the decimal point. */
	private static final int MAX_WHOLE_DIGITS = 12;

	/** As many as a double's shortest form has after the point (0.1 + 0.2 is 0.30000000000000004). */
	private static final int MAX_DECIMALS = 20;

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

	/**
	 * The field's amount, a number or a number written as text; zero without one, or with one no salary could be. A
	 * board is anyone's to write: "1E+1000000000" is a short text, but writing it out, or rounding "1E-1000000000" to
	 * cents, would take a number of a billion digits. So the amount is checked before anything is done with it.
	 */
	private static BigDecimal amount(JsonNode value) {
		try {
			BigDecimal amount = value.isNumber() ? value.decimalValue()
					: value.isString() ? new BigDecimal(value.asString().strip()) : BigDecimal.ZERO;
			return isSalary(amount) ? amount : BigDecimal.ZERO;
		} catch (NumberFormatException | JacksonException _) {
			// Text that isn't a number, or a number a double can't hold (1e400), which Jackson refuses to convert
			return BigDecimal.ZERO;
		}
	}

	/** Up to a trillion, with no more decimals than a double writes: read without any arithmetic. */
	private static boolean isSalary(BigDecimal amount) {
		return amount.precision() - amount.scale() <= MAX_WHOLE_DIGITS && amount.scale() <= MAX_DECIMALS;
	}

	/** With thousands separators, whatever the server's language, and cents only when it has them: 60,000 or 23.75. */
	private static String formatted(BigDecimal amount) {
		BigDecimal plain = amount.stripTrailingZeros();
		return (plain.scale() <= 0) ? String.format(Locale.ROOT, "%,d", plain.toBigInteger())
				: String.format(Locale.ROOT, "%,.2f", amount);
	}

}
