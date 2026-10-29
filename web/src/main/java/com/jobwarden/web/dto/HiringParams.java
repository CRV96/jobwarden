package com.jobwarden.web.dto;

import java.util.HashMap;
import java.util.Map;

import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.enums.PostedWithin;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.enums.HiringTab;
import com.jobwarden.web.enums.RecruiterFilter;
import org.jspecify.annotations.Nullable;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * What the Who's hiring page shows, from the query parameters of its address, e.g.
 * {@code /hiring?tab=FOLLOWING&q=java&posted=WEEK&country=RO&tag=hiring}, or
 * {@code /hiring?tab=RECRUITERS&who=FOUND}. A parameter left out means its default. The page's links are built from
 * it, so each keeps everything else.
 *
 * @param q only posts whose text, role, company or author has this; on the Recruiters tab, the recruiters whose name,
 * headline or company has it
 * @param country an ISO 3166 code
 * @param tag a hashtag, without the #
 * @param recruiter only this recruiter's posts
 * @param who which recruiters the Recruiters tab shows
 */
public record HiringParams(HiringTab tab, @Nullable String q, PostedWithin posted, @Nullable String country,
		@Nullable String tag, @Nullable Long recruiter, RecruiterFilter who) {

	/**
	 * Spring binds the query parameters through this constructor, with {@code null} for each one that's missing.
	 */
	public HiringParams {
		tab = (tab != null) ? tab : HiringTab.POSTS;
		q = FormValues.blankToNull(q);
		posted = (posted != null) ? posted : PostedWithin.ANY;
		country = FormValues.blankToNull(country);
		tag = FormValues.blankToNull(tag);
		who = (who != null) ? who : RecruiterFilter.ALL;
	}

	/**
	 * The page as it first opens: every post, the latest first.
	 */
	public static HiringParams defaults() {
		return new HiringParams(null, null, null, null, null, null, null);
	}

	public HiringPostQuery toQuery() {
		return HiringPostQuery.builder()
			.search(this.q)
			.postedWithin(this.posted)
			.country(this.country)
			.tag(this.tag)
			.followedOnly(this.tab == HiringTab.FOLLOWING)
			.recruiterId(this.recruiter)
			.build();
	}

	/**
	 * The page's address, with only the parameters that aren't their default.
	 */
	public String url() {
		UriComponentsBuilder url = UriComponentsBuilder.fromPath(ControllerConstants.HIRING);
		Map<String, Object> values = new HashMap<>();
		if (this.tab != HiringTab.POSTS) {
			url.queryParam("tab", this.tab);
		}
		// As variables, which encode() encodes strictly: in the text itself, a + would come back as a space
		add(url, values, "q", this.q);
		if (this.posted != PostedWithin.ANY) {
			url.queryParam("posted", this.posted);
		}
		add(url, values, "country", this.country);
		add(url, values, "tag", this.tag);
		if (this.recruiter != null) {
			url.queryParam("recruiter", this.recruiter);
		}
		if (this.who != RecruiterFilter.ALL) {
			url.queryParam("who", this.who);
		}
		return url.encode().buildAndExpand(values).toUriString();
	}

	/**
	 * Another tab starts afresh: only the search goes along.
	 */
	public HiringParams withTab(HiringTab tab) {
		return new HiringParams(tab, this.q, PostedWithin.ANY, null, null, null, RecruiterFilter.ALL);
	}

	public HiringParams withPosted(PostedWithin posted) {
		return new HiringParams(this.tab, this.q, posted, this.country, this.tag, this.recruiter, this.who);
	}

	/**
	 * @param tag {@code null} for every tag
	 */
	public HiringParams withTag(@Nullable String tag) {
		return new HiringParams(this.tab, this.q, this.posted, this.country, tag, this.recruiter, this.who);
	}

	public HiringParams withWho(RecruiterFilter who) {
		return new HiringParams(this.tab, this.q, this.posted, this.country, this.tag, this.recruiter, who);
	}

	/**
	 * One recruiter's posts, every one of them.
	 */
	public static HiringParams ofRecruiter(long recruiterId) {
		return new HiringParams(HiringTab.POSTS, null, PostedWithin.ANY, null, null, recruiterId, RecruiterFilter.ALL);
	}

	public boolean isFiltered() {
		return this.q != null || this.posted != PostedWithin.ANY || this.country != null || this.tag != null
				|| this.recruiter != null;
	}

	private static void add(UriComponentsBuilder url, Map<String, Object> values, String name, @Nullable String value) {
		if (value != null) {
			url.queryParam(name, "{" + name + "}");
			values.put(name, value);
		}
	}

}
