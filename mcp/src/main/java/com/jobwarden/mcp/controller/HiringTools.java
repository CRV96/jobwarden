package com.jobwarden.mcp.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jobwarden.hiring.dto.AddedHiringPosts;
import com.jobwarden.hiring.dto.AddedRecruiters;
import com.jobwarden.hiring.dto.ContactJob;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.NewHiringPost;
import com.jobwarden.hiring.dto.NewRecruiter;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.service.HiringService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.date;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * Who's hiring: the recruiters the profile knows and follows, and the posts saying they're hiring that the Who's
 * hiring page shows.
 */
@Component
@RequiredArgsConstructor
class HiringTools implements McpToolSet {

	private static final int MAX_POSTS_AT_ONCE = 50;

	private static final int MAX_LISTED = 200;

	private static final int MAX_RECRUITERS_AT_ONCE = 50;

	private final Profiles profiles;

	private final HiringService hiring;

	@Override
	public List<McpTool> tools() {
		return List.of(listRecruiters(), addRecruiters(), listHiringPosts(), addHiringPosts());
	}

	private McpTool listRecruiters() {
		return new McpTool("list_recruiters", "List recruiters",
				"The recruiters and hiring managers the profile knows, from the Who's hiring page: those it follows first, "
						+ "then those with the most posts. Look at the followed ones' recent posts first. The contacts saved "
						+ "on its jobs are among them; those it removed aren't, and can't be added again.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())), true, false, call -> {
					long userId = this.profiles.of(call);
					return Map.of("recruiters", this.hiring.findRecruiters(userId).stream().map(HiringTools::row).toList());
				});
	}

	private McpTool addRecruiters() {
		ObjectNode recruiter = object(properties(
				"name", string("Their name, as their profile shows it"),
				"headline", string("What they do, as their profile says, e.g. Technical Recruiter at Acme"),
				"profile_url", string("Their profile, e.g. https://www.linkedin.com/in/name"),
				"company", string("Where they recruit, or the agency they recruit from"),
				"country", string("Where they recruit, as an ISO 3166 code, e.g. RO"),
				"note", string("Why they're worth following, in a sentence: the roles they hire for, the jobs of the "
						+ "profile's they're behind")),
				"name", "profile_url");
		return new McpTool("add_recruiters", "Add recruiters",
				"Adds recruiters, talent partners and hiring managers you found to the profile's Who's hiring page, to "
						+ "follow. Only real people who recruit for the roles now. Profiles the profile already has, removed "
						+ "ones included, and those that aren't web addresses, are left out and listed as skipped. At most "
						+ MAX_RECRUITERS_AT_ONCE + " at once.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"recruiters", list(recruiter, "The recruiters found")), "recruiters"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<ToolCall> items = call.items("recruiters");
					if (items.isEmpty() || items.size() > MAX_RECRUITERS_AT_ONCE) {
						throw new ToolException("recruiters must have from 1 to " + MAX_RECRUITERS_AT_ONCE + " people");
					}
					AddedRecruiters added = this.hiring.addFoundRecruiters(userId, items.stream()
						.map(item -> NewRecruiter.builder()
							.name(item.requiredString("name"))
							.headline(item.string("headline"))
							.profileUrl(item.requiredString("profile_url"))
							.company(item.string("company"))
							.country(item.string("country"))
							.note(item.string("note"))
							.build())
						.toList());
					return Map.of("added", added.added().stream().map(HiringTools::row).toList(), "skipped",
							added.skipped());
				});
	}

	private McpTool listHiringPosts() {
		return new McpTool("list_hiring_posts", "List hiring posts",
				"The hiring posts the profile has, the latest first, to skip them when adding more: their links, roles and "
						+ "authors. Hidden ones are left out but can't be added again either.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"limit", integer("How many, at most; " + MAX_LISTED + " if left out"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					int limit = call.limit("limit", MAX_LISTED, MAX_LISTED);
					return Map.of("posts", this.hiring.findPosts(userId, HiringPostQuery.ALL)
						.stream()
						.limit(limit)
						.map(HiringTools::row)
						.toList());
				});
	}

	private McpTool addHiringPosts() {
		ObjectNode author = object(properties(
				"name", string("Their name, as their profile shows it"),
				"headline", string("What they do, as their profile says, e.g. IT Recruiter at Acme"),
				"profile_url", string("Their profile, e.g. https://www.linkedin.com/in/name")),
				"name", "profile_url");
		ObjectNode post = object(properties(
				"link", string("The post's own address"),
				"text", string("The post as posted, shortened when it's long, never rewritten"),
				"posted_on", date("The day it was posted, as YYYY-MM-DD"),
				"role", string("The job it's for, e.g. Java Developer"),
				"company", string("The company hiring"),
				"country", string("Where the job is, as an ISO 3166 code, e.g. RO"),
				"author", author),
				"link", "text", "author");
		return new McpTool("add_hiring_posts", "Add hiring posts",
				"Adds recruiters' and hiring managers' posts saying they're hiring to the profile's Who's hiring page, with "
						+ "their authors. Only real posts about open roles. Links the profile already has, and posts whose "
						+ "link or author's profile isn't a web address, are left out and listed as skipped. At most "
						+ MAX_POSTS_AT_ONCE + " at once.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "posts", list(post, "The posts found")), "posts"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					List<ToolCall> items = call.items("posts");
					if (items.isEmpty() || items.size() > MAX_POSTS_AT_ONCE) {
						throw new ToolException("posts must have from 1 to " + MAX_POSTS_AT_ONCE + " posts");
					}
					List<NewHiringPost> found = items.stream().map(item -> {
						ToolCall by = item.requiredObject("author");
						return NewHiringPost.builder()
							.link(item.requiredString("link"))
							.text(item.requiredString("text"))
							.postedOn(item.date("posted_on"))
							.role(item.string("role"))
							.company(item.string("company"))
							.country(item.string("country"))
							.authorName(by.requiredString("name"))
							.authorHeadline(by.string("headline"))
							.authorProfileUrl(by.requiredString("profile_url"))
							.build();
					}).toList();
					AddedHiringPosts added = this.hiring.addPosts(userId, found);
					return Map.of("added", added.added().stream().map(HiringTools::row).toList(), "skipped", added.skipped());
				});
	}

	private static RecruiterRow row(Recruiter recruiter) {
		return new RecruiterRow(recruiter.id(), recruiter.name(), recruiter.headline(), recruiter.profileUrl(),
				recruiter.company(), recruiter.country(), recruiter.origin(), recruiter.followed(), recruiter.posts(),
				recruiter.contactFor().stream().map(ContactJob::applicationId).toList());
	}

	private static PostRow row(HiringPost post) {
		return new PostRow(post.id(), post.link(), post.role(), post.company(), post.country(), post.postedOn(),
				post.recruiter().name(), post.applicationId());
	}

	/**
	 * @param origin how they came: POST (a post's author), FOUND (by Claude), CONTACT (on a job) or HAND
	 * @param contactFor the jobs they're the contact for
	 */
	record RecruiterRow(long id, String name, @Nullable String headline, String profileUrl, @Nullable String company,
			@Nullable String country, RecruiterOrigin origin, boolean followed, long posts, List<Long> contactFor) {
	}

	/**
	 * @param jobId the job it was saved as, if it was
	 */
	record PostRow(long id, String link, @Nullable String role, @Nullable String company, @Nullable String country,
			@Nullable LocalDate postedOn, String author, @Nullable Long jobId) {
	}

}
