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
import static com.jobwarden.mcp.controller.Schemas.bool;
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

	/** How many a list gives unless asked for more: adding leaves out what the profile has, so lists are rarely needed. */
	private static final int LISTED = 50;

	private static final int MAX_RECRUITERS_AT_ONCE = 50;

	private final Profiles profiles;

	private final HiringService hiring;

	@Override
	public List<McpTool> tools() {
		return List.of(listRecruiters(), addRecruiters(), listHiringPosts(), addHiringPosts());
	}

	private McpTool listRecruiters() {
		return new McpTool("list_recruiters", "List recruiters",
				"Recruiters and hiring managers the profile knows (Who's hiring page), followed ones first; its jobs' "
						+ "contacts included, removed ones not. Not needed before add_recruiters: it skips known ones.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"followed_only", bool(),
						"limit", integer("Default " + LISTED + ", max " + MAX_LISTED))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					boolean followedOnly = call.arguments().path("followed_only").asBoolean(false);
					return Map.of("recruiters", this.hiring.findRecruiters(userId)
						.stream()
						.filter(recruiter -> !followedOnly || recruiter.followed())
						.limit(call.limit("limit", LISTED, MAX_LISTED))
						.map(HiringTools::row)
						.toList());
				});
	}

	private McpTool addRecruiters() {
		ObjectNode recruiter = object(properties(
				"name", string(),
				"headline", string("e.g. Technical Recruiter at Acme"),
				"profile_url", string("e.g. https://www.linkedin.com/in/name"),
				"company", string("Employer or agency"),
				"country", string("ISO 3166 code, e.g. RO"),
				"note", string("Why follow them, one sentence")),
				"name", "profile_url");
		return new McpTool("add_recruiters", "Add recruiters",
				"Adds recruiters and hiring managers you found to the Who's hiring page. Only real people recruiting for "
						+ "the roles now; known or removed profiles, and non-URLs, are skipped. Max " + MAX_RECRUITERS_AT_ONCE
						+ ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "recruiters", list(recruiter)), "recruiters"),
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
					return Map.of("added", added.added().size(), "skipped", added.skipped());
				});
	}

	private McpTool listHiringPosts() {
		return new McpTool("list_hiring_posts", "List hiring posts",
				"The profile's hiring posts, latest first: links, roles and authors; hidden ones left out. Not needed "
						+ "before add_hiring_posts: it skips known links.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"limit", integer("Default " + LISTED + ", max " + MAX_LISTED))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					int limit = call.limit("limit", LISTED, MAX_LISTED);
					return Map.of("posts", this.hiring.findPosts(userId, HiringPostQuery.ALL)
						.stream()
						.limit(limit)
						.map(HiringTools::row)
						.toList());
				});
	}

	private McpTool addHiringPosts() {
		ObjectNode author = object(properties(
				"name", string(),
				"headline", string("e.g. IT Recruiter at Acme"),
				"profile_url", string("e.g. https://www.linkedin.com/in/name")),
				"name", "profile_url");
		ObjectNode post = object(properties(
				"link", string("The post's own URL"),
				"text", string("As posted, shortened if long, never rewritten"),
				"posted_on", date(),
				"role", string("e.g. Java Developer"),
				"company", string("Who's hiring"),
				"country", string("The job's, ISO 3166 code"),
				"author", author),
				"link", "text", "author");
		return new McpTool("add_hiring_posts", "Add hiring posts",
				"Adds posts by recruiters or hiring managers saying they're hiring to the Who's hiring page. Only real "
						+ "posts about open roles; known links and non-URLs are skipped. Max " + MAX_POSTS_AT_ONCE + ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "posts", list(post)), "posts"),
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
					return Map.of("added", added.added().size(), "skipped", added.skipped());
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
	 * @param origin how they came: POST (a post's author), FOUND (by the assistant), CONTACT (on a job) or HAND
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
