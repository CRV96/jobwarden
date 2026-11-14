package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jobwarden.search.Regions;
import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.LOCATION;
import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;
import static com.jobwarden.watchlist.service.impl.FeedJson.workType;

/**
 * Ashby's job boards (api.ashbyhq.com): every job with its description and pay; one that isn't listed is left out.
 */
final class AshbyFeed implements BoardFeed {

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/posting-api/job-board/" + board + "?includeCompensation=true");
	}

	@Override
	public String jobsField() {
		return "jobs";
	}

	@Override
	public @Nullable Posting posting(JsonNode job) {
		String title = text(job, "title");
		String link = text(job, "jobUrl");
		if (title == null || link == null || !job.path("isListed").asBoolean(true)) {
			return null;
		}
		List<JsonNode> secondary = job.path("secondaryLocations").values().stream().toList();
		String location = Stream.concat(Stream.of(text(job, LOCATION)), secondary.stream().map(place -> text(place, LOCATION)))
			.filter(place -> place != null)
			.distinct()
			.collect(Collectors.joining("; "));
		Set<String> countries = new LinkedHashSet<>();
		Stream.concat(Stream.of(job), secondary.stream())
			.map(AshbyFeed::country)
			.filter(country -> country != null)
			.forEach(country -> countries.addAll(Regions.countriesOf(country)));
		Employment employment = Employment.of(text(job, "employmentType"));
		JsonNode compensation = job.path("compensation");
		String salary = text(compensation, "scrapeableCompensationSalarySummary");
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(location.isEmpty() ? null : location)
			.countries(countries)
			.workType(workTypeOf(job))
			.employmentStated(employment.stated())
			.employmentType(employment.type())
			.link(link)
			.publishedAt(instant(text(job, "publishedAt")))
			.salary((salary != null) ? salary : text(compensation, "compensationTierSummary"))
			.description(shortened(text(job, "descriptionPlain")))
			.build();
	}

	/**
	 * Where a location's country is: the job's own address has it under {@code postalAddress}, a secondary location's
	 * address has it right under {@code address}, as Ashby documents them. Each is read for both, in case a board's
	 * answer differs.
	 */
	private static @Nullable String country(JsonNode place) {
		JsonNode address = place.path("address");
		String country = text(address.path("postalAddress"), "addressCountry");
		return (country != null) ? country : text(address, "addressCountry");
	}

	/**
	 * {@code workplaceType} says Remote, Hybrid or OnSite. {@code isRemote} is true for Hybrid as well, so it only counts
	 * when there's no workplace type to go by.
	 */
	private static @Nullable WorkType workTypeOf(JsonNode job) {
		WorkType stated = workType(text(job, "workplaceType"));
		if (stated != null) {
			return stated;
		}
		return job.path("isRemote").asBoolean(false) ? WorkType.REMOTE : null;
	}

}
