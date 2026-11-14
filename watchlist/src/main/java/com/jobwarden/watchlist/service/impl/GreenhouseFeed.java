package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.time.Instant;

import org.jsoup.parser.Parser;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import static com.jobwarden.watchlist.service.impl.FeedJson.LOCATION;
import static com.jobwarden.watchlist.service.impl.FeedJson.instant;
import static com.jobwarden.watchlist.service.impl.FeedJson.shortened;
import static com.jobwarden.watchlist.service.impl.FeedJson.text;
/**
 * Greenhouse's job boards (boards-api.greenhouse.io): the list leaves the descriptions out, which are read one job at a
 * time, for the postings that match.
 */
final class GreenhouseFeed implements BoardFeed {

	@Override
	public URI jobs(URI server, String board) {
		return URI.create(server + "/v1/boards/" + board + "/jobs");
	}

	@Override
	public String jobsField() {
		return "jobs";
	}

	@Override
	public @Nullable Posting posting(JsonNode job) {
		String title = text(job, "title");
		String link = text(job, "absolute_url");
		if (title == null || link == null) {
			return null;
		}
		Instant published = instant(text(job, "first_published"));
		return Posting.builder()
			.id(job.path("id").asString(""))
			.title(title)
			.location(text(job.path(LOCATION), "name"))
			.link(link)
			.publishedAt((published != null) ? published : instant(text(job, "updated_at")))
			.company(text(job, "company_name"))
			.build();
	}

	@Override
	public URI description(URI server, String board, String posting) {
		return URI.create(server + "/v1/boards/" + board + "/jobs/" + posting);
	}

	/**
	 * The HTML comes escaped: &lt;p&gt;
	 */
	@Override
	public @Nullable String descriptionOf(JsonNode job) {
		return shortened(HtmlText.of(Parser.unescapeEntities(job.path("content").asString(""), false)));
	}

}
