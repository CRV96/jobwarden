package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.LOCATION;
import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;

/**
 * SmartRecruiters' job boards (api.smartrecruiters.com): a company's public postings, a hundred at a time, without
 * their descriptions, which are read one posting at a time, for those that match. A company the API doesn't know has
 * a board with no jobs: it doesn't answer otherwise.
 */
final class SmartRecruitersFeed implements BoardFeed {

	/** The most postings the API gives at once. */
	static final int PAGE_SIZE = 100;

	/** Where people see a company's postings: jobs.smartrecruiters.com/BoschGroup/744000163282223. */
	private static final String JOB_PAGES = "https://jobs.smartrecruiters.com/";

	/** The parts of a description that say what the job is and asks for: the company's own intro is left out. */
	private static final List<String> SECTIONS = List.of("jobDescription", "qualifications", "additionalInformation");

	@Override
	public URI jobs(URI server, String board) {
		return page(server, board, 0);
	}

	@Override
	public String jobsField() {
		return "content";
	}

	/**
	 * A page that isn't full is the last.
	 */
	@Override
	public @Nullable URI next(URI server, String board, int pages, int read, int onPage) {
		return (onPage < PAGE_SIZE) ? null : page(server, board, read);
	}

	@Override
	public @Nullable Posting posting(JsonNode job, Board board) {
		String title = text(job, "name");
		String id = text(job, "id");
		String company = text(job.path("company"), "identifier");
		String visibility = text(job, "visibility");
		if (title == null || id == null || company == null
				|| (visibility != null && !visibility.equalsIgnoreCase("public"))) {
			return null;
		}
		JsonNode place = job.path(LOCATION);
		String country = text(place, "country");
		JsonNode employment = job.path("typeOfEmployment");
		String kind = text(employment, "label");
		Employment contract = Employment.of((kind != null) ? kind : text(employment, "id"));
		return Posting.builder()
			.id(id)
			.title(title)
			.location(text(place, "fullLocation"))
			.countries((country != null && country.length() == 2) ? Set.of(country.toUpperCase(Locale.ROOT)) : Set.of())
			.workType(workType(place))
			.employmentStated(contract.stated())
			.employmentType(contract.type())
			.link(JOB_PAGES + BoardFeeds.encoded(company) + "/" + BoardFeeds.encoded(id))
			.publishedAt(instant(text(job, "releasedDate")))
			.company(text(job.path("company"), "name"))
			.build();
	}

	@Override
	public URI description(URI server, String board, String posting) {
		return URI.create(server + "/v1/companies/" + board + "/postings/" + posting);
	}

	/**
	 * The job's sections, each under its title: Job Description, Qualifications, Additional Information.
	 */
	@Override
	public @Nullable String descriptionOf(JsonNode job) {
		JsonNode sections = job.path("jobAd").path("sections");
		StringBuilder text = new StringBuilder();
		for (String name : SECTIONS) {
			JsonNode section = sections.path(name);
			String body = HtmlText.of(text(section, "text"));
			if (body != null) {
				String heading = text(section, "title");
				text.append("\n\n").append((heading != null) ? heading + "\n" : "").append(body);
			}
		}
		return shortened(text.toString().strip());
	}

	private static URI page(URI server, String board, int offset) {
		return URI.create(server + "/v1/companies/" + board + "/postings?limit=" + PAGE_SIZE + "&offset=" + offset);
	}

	/**
	 * Its location says remote or hybrid; when it says neither, it's on site. An older posting that says nothing of
	 * either says nothing of its work type.
	 */
	private static @Nullable WorkType workType(JsonNode place) {
		JsonNode remote = place.path("remote");
		JsonNode hybrid = place.path("hybrid");
		if (remote.asBoolean(false)) {
			return WorkType.REMOTE;
		}
		if (hybrid.asBoolean(false)) {
			return WorkType.HYBRID;
		}
		return (remote.isBoolean() && hybrid.isBoolean()) ? WorkType.ONSITE : null;
	}

}
