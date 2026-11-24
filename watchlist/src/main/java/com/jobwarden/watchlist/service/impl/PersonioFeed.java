package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;
import static com.jobwarden.watchlist.service.impl.FeedXml.each;

/**
 * Personio's job boards, each a host of its own (acme.jobs.personio.de/xml): every job at once, as XML, with its
 * description in parts and, now and then, its pay. A job doesn't say its link: it's on its board,
 * acme.jobs.personio.de/job/123. The board is the same on jobs.personio.com.
 * <p>
 * The feed is in one language at a time, and a job written in one language only has no description in the others:
 * the board is read in English, then in the company's own language, for the jobs written in that one only.
 */
final class PersonioFeed implements BoardFeed {

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/xml?language=en");
	}

	/**
	 * After the English, the company's own language: none after an empty board.
	 */
	@Override
	public @Nullable URI next(URI server, String board, int pages, int read, int onPage) {
		return (pages == 1 && onPage > 0) ? URI.create(server + "/xml") : null;
	}

	@Override
	public String jobsField() {
		return "position";
	}

	@Override
	public boolean xml() {
		return true;
	}

	/**
	 * Its offices are its places, e.g. Berlin; Remote: the matching reads their countries, and whether they say
	 * remote, from their words.
	 */
	@Override
	public @Nullable Posting posting(JsonNode job, Board board) {
		String title = text(job, "name");
		String id = text(job, "id");
		if (title == null || id == null) {
			return null;
		}
		String offices = Stream
			.concat(Stream.of(text(job, "office")), each(job.path("additionalOffices").path("office")).stream()
				.map(office -> office.isString() ? office.asString().strip() : null))
			.filter(office -> office != null && !office.isEmpty())
			.distinct()
			.collect(Collectors.joining(Posting.PLACES));
		Employment employment = Employment.of(contract(text(job, "employmentType"), text(job, "schedule")));
		return Posting.builder()
			.id(id)
			.title(title)
			.location(offices.isEmpty() ? null : offices)
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(board.url() + "/job/" + BoardFeeds.encoded(id))
			.publishedAt(instant(text(job, "createdAt")))
			.salary(salary(job.path("salaryInformation")))
			.description(description(job))
			.build();
	}

	/**
	 * Its kind (permanent, intern, working_student, trainee, freelance, temporary) with its hours (full-time,
	 * part-time, full-or-part-time), in words {@link Employment} reads: a working student is a Werkstudent; a job full-
	 * or part-time, as the candidate likes, is the kind alone.
	 */
	static String contract(@Nullable String kind, @Nullable String schedule) {
		String words = Objects.requireNonNullElse(kind, "").replace("working_student", "werkstudent");
		return (schedule == null || schedule.equals("full-or-part-time")) ? words : words + " " + schedule;
	}

	/**
	 * {"min": "80000.00", "max": "100000.00", "currencyCode": "EUR", "type": "yearly"}: EUR 80,000–100,000 per year.
	 */
	private static @Nullable String salary(JsonNode salary) {
		String type = text(salary, "type");
		String interval = (type == null) ? "" : switch (type.toLowerCase(Locale.ROOT)) {
			case "yearly" -> "per year";
			case "monthly" -> "per month";
			case "weekly" -> "per week";
			case "daily" -> "per day";
			case "hourly" -> "per hour";
			default -> type;
		};
		return SalaryRange.of(Objects.requireNonNullElse(text(salary, "currencyCode"), ""), salary.path("min"),
				salary.path("max"), interval);
	}

	/** Its parts, each under its heading: The Role, What you'll do, What you need... */
	private static @Nullable String description(JsonNode job) {
		StringBuilder text = new StringBuilder();
		for (JsonNode part : each(job.path("jobDescriptions").path("jobDescription"))) {
			String body = HtmlText.of(text(part, "value"));
			if (body != null) {
				String heading = text(part, "name");
				text.append("\n\n").append((heading != null) ? heading + "\n" : "").append(body);
			}
		}
		return shortened(text.toString().strip());
	}

}
