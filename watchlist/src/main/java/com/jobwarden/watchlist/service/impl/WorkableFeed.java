package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;

/**
 * Workable's job boards, as the widget companies put on their careers sites reads them
 * (apply.workable.com/api/v1/widget): every job at once, with its description, but no pay. A job in several places
 * is in the list once for each, all with the same link: {@link BoardFeeds} puts them together.
 */
final class WorkableFeed implements BoardFeed {

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/api/v1/widget/accounts/" + board + "?details=true");
	}

	@Override
	public String jobsField() {
		return "jobs";
	}

	/**
	 * Its places are those its board shows: one a company hides (a remote job's, say) is left out of the location,
	 * though its country still counts for the searches' regions. The widget only says whether a job is remote: one
	 * that isn't may be on site or hybrid.
	 */
	@Override
	public @Nullable Posting posting(JsonNode job, Board board) {
		String title = text(job, "title");
		String link = text(job, "url");
		if (link == null) {
			link = text(job, "shortlink");
		}
		if (title == null || link == null) {
			return null;
		}
		List<JsonNode> places = job.path("locations").values().stream().toList();
		if (places.isEmpty()) {
			// An older answer: the one place, on the job itself
			places = List.of(job);
		}
		String location = places.stream()
			.filter(place -> !place.path("hidden").asBoolean(false))
			.map(WorkableFeed::place)
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.joining(Posting.PLACES));
		Set<String> countries = new LinkedHashSet<>();
		for (JsonNode place : places) {
			String code = text(place, "countryCode");
			if (code != null && code.length() == 2) {
				countries.add(code.toUpperCase(Locale.ROOT));
			}
		}
		Employment employment = Employment.of(text(job, "employment_type"));
		String published = text(job, "published_on");
		return Posting.builder()
			.id(Objects.requireNonNullElse(text(job, "shortcode"), ""))
			.title(title)
			.location(location.isEmpty() ? null : location)
			.countries(countries)
			.workType(job.path("telecommuting").asBoolean(false) ? WorkType.REMOTE : null)
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt(instant((published != null) ? published : text(job, "created_at")))
			.description(shortened(HtmlText.of(text(job, "description"))))
			.build();
	}

	/**
	 * @return e.g. Paris, France; {@code null} when it names neither
	 */
	private static @Nullable String place(JsonNode place) {
		String places = Stream.of(text(place, "city"), text(place, "country"))
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));
		return places.isEmpty() ? null : places;
	}

}
