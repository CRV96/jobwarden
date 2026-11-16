package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.WorkType;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.service.WatchlistService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.bool;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * What the profile asked the assistant to do from its pages: the requests waiting, each with step-by-step
 * instructions, and finishing them. The page shows each request's status and summary, and the jobs found for it.
 * <p>
 * A request comes with what its steps need, so the assistant needn't ask for it call by call: the job it's about, the
 * CV, the story bank, the saved searches, what the profile kept and discarded, the recruiters it follows, the
 * companies of its jobs. The steps lean on what costs the least: the server leaving out what the profile has,
 * filter_links before opening search results, pages read with a question rather than whole, check_postings for many
 * postings at once. They name no assistant: Claude Code and Codex read the same.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class RequestTools implements McpToolSet {

	private static final int SUMMARY_MAX_LENGTH = 2000;

	/** The most of the story bank an interview's request hands out, in characters; list_stories has the rest. */
	static final int STORIES_MAX_LENGTH = 8_000;

	/**
	 * The most a request's result is, in characters, as the assistant reads it: well under what Codex reads of a tool's
	 * result whole (10,000 tokens), and what McpBudgetTests checks (25,000).
	 */
	static final int REQUEST_MAX_LENGTH = 23_000;

	/** The least of the CV file's text, and of a posting's description, a request keeps when it has to cut. */
	static final int LEAST_TEXT = 2_000;

	/** How long after applying an application with no news counts as No response, and when to follow up before. */
	static final int NO_RESPONSE_AFTER_DAYS = 30;

	static final int FOLLOW_UP_AFTER_DAYS = 7;

	/** How much of each the request brings along: enough to go by, little enough to read at a glance. */
	private static final int TASTE = 10;

	private static final int FOLLOWED = 25;

	private static final int COMPANIES = 20;

	private static final int WATCHED_COMPANIES = 50;

	/** The most people a job's messages are written to: a few, well chosen. */
	private static final int RECIPIENTS = 5;

	private final Profiles profiles;

	private final DiscoveryService discovery;

	private final HiringService hiring;

	private final ApplicationService applications;

	private final WatchlistService watchlist;

	private final CoverLetterService letters;

	private final CvTools cvs;

	private final StoryTools stories;

	private final DiscoverTools searches;

	private final FitTools fits;

	private final AutoApplyService autoApply;

	private final JsonMapper json;

	/** Writes results as the MCP server does, to measure a request before handing it out; made once, when first needed. */
	private @Nullable JsonMapper results;

	@Override
	public List<McpTool> tools() {
		return List.of(listRequests(), getRequest(), finishRequest());
	}

	private McpTool listRequests() {
		return new McpTool("list_requests", "List requests",
				"The requests the profile is waiting for, oldest first. Do each with get_request.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "all", bool("Also finished ones, latest first"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					boolean all = call.arguments().path("all").asBoolean(false);
					List<AiRequest> requests = all ? this.discovery.findRequests(userId, 50) : this.discovery.findOpenRequests(userId);
					// Unattended, Auto-apply's aren't there: they need the profile at the browser
					return Map.of("requests", requests.stream()
						.filter(request -> !call.unattended() || request.kind() != AiRequestKind.AUTO_APPLY)
						.map(RequestTools::row)
						.toList());
				});
	}

	private McpTool getRequest() {
		return new McpTool("get_request", "Get a request",
				"A request: its steps and the data they need. Marks it as being done; end it with finish_request.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "request_id", integer()), "request_id"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					requireAttended(call, userId, call.requiredNumber("request_id"));
					AiRequest request = this.discovery.startRequest(userId, call.requiredNumber("request_id"));
					log.info("{} started request {} ({}) of profile {}", call.client(), request.id(), request.kind(), userId);
					return details(userId, request);
				});
	}

	/**
	 * Auto-apply's requests are out of an unattended run's reach, to start or to end: they need the profile at the
	 * browser, whatever the run reads.
	 */
	private void requireAttended(ToolCall call, long userId, long requestId) {
		if (call.unattended() && this.discovery.findRequest(userId, requestId).kind() == AiRequestKind.AUTO_APPLY) {
			throw new ToolException("Auto-apply needs the profile at the browser, with Claude in Chrome: leave this request");
		}
	}

	/**
	 * Auto-apply's request ends with its queue: an assistant that can't apply (the loop, Run, Codex) can't end it
	 * while jobs wait, whatever it reads.
	 */
	private void requireApplied(long userId, long requestId, boolean done) {
		if (done && this.discovery.findRequest(userId, requestId).kind() == AiRequestKind.AUTO_APPLY
				&& !this.autoApply.findOpenItems(userId).isEmpty()) {
			throw new ToolException("Auto-apply ends when its queue is done, in the profile's browser with Claude in Chrome: "
					+ "leave this request");
		}
	}

	private McpTool finishRequest() {
		return new McpTool("finish_request", "Finish a request",
				"Marks a request done, or failed, with a summary the profile reads.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"request_id", integer(),
						"summary", string("What you did and found, 1-3 sentences, or why you couldn't"),
						"done", bool("false if you couldn't; default true")),
						"request_id", "summary"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					String summary = call.requiredString("summary");
					requireAttended(call, userId, call.requiredNumber("request_id"));
					requireApplied(userId, call.requiredNumber("request_id"), call.arguments().path("done").asBoolean(true));
					AiRequest request = this.discovery.finishRequest(userId, call.requiredNumber("request_id"),
							call.arguments().path("done").asBoolean(true),
							(summary.length() <= SUMMARY_MAX_LENGTH) ? summary : summary.substring(0, SUMMARY_MAX_LENGTH - 1) + "…");
					log.info("{} finished request {} ({}) of profile {}: {}", call.client(), request.id(), request.kind(), userId,
							request.status());
					return row(request);
				});
	}

	/**
	 * The request, with its steps and what they need, so the assistant needn't ask for it: the job it's about, the CV
	 * (brief, or full for what's written from it), the story bank, the saved searches, the jobs to score; what the
	 * profile kept and discarded when finding jobs, the recruiters it follows for hiring posts, its jobs' companies for
	 * recruiters. Auto-apply's answers never come with it: the terminal loop may get requests, and never those.
	 */
	private RequestDetails details(long userId, AiRequest request) {
		AiRequestKind kind = request.kind();
		Taste taste = kind.findsJobs() ? taste(userId) : null;
		List<RecruiterRef> followed = (kind == AiRequestKind.FIND_HIRING_POSTS) ? this.hiring.findRecruiters(userId)
			.stream()
			.filter(Recruiter::followed)
			.limit(FOLLOWED)
			.map(recruiter -> new RecruiterRef(recruiter.name(), recruiter.profileUrl()))
			.toList() : List.of();
		List<String> companies = (kind == AiRequestKind.FIND_RECRUITERS) ? companies(userId) : List.of();
		Long jobId = request.applicationId();
		ApplicationDetails job = (kind.isAboutAJob() && jobId != null) ? this.applications.findById(userId, jobId)
			.orElse(null) : null;
		List<RecipientRef> recipients = (kind == AiRequestKind.WRITE_OUTREACH && job != null) ? recipients(userId, job)
				: List.of();
		String baseLetter = (kind == AiRequestKind.WRITE_COVER_LETTER) ? baseLetter(userId, job) : null;
		List<StoryTools.StoryView> bank = (kind == AiRequestKind.PREPARE_INTERVIEW) ? this.stories.views(userId) : List.of();
		// Only the names: the boards are JobWarden's to read
		List<String> watched = (taste != null) ? this.watchlist.findCompanies(userId)
			.stream()
			.limit(WATCHED_COMPANIES)
			.map(WatchedCompany::name)
			.toList() : List.of();
		List<DiscoverTools.SearchView> searches = (kind == AiRequestKind.RUN_SAVED_SEARCHES) ? this.searches.searches(userId)
				: List.of();
		List<FitTools.JobRef> toScore = (kind == AiRequestKind.SCORE_JOBS) ? this.fits.unscored(userId, RequestInstructions.SCORE_AT_ONCE)
				: List.of();
		// The whole of it within what the assistant reads at once: what's long gives way, the stories first (to their
		// titles), then the CV file's text, then the CV fields' texts, then the posting's description
		int storiesMax = STORIES_MAX_LENGTH;
		int fileTextMax = CvTools.REQUEST_TEXT_LENGTH;
		int fieldTextMax = Integer.MAX_VALUE;
		int descriptionMax = JobTools.DESCRIPTION_MAX_LENGTH;
		int descriptionLength = (job != null && job.description() != null) ? job.description().length() : 0;
		RequestDetails details = null;
		for (int round = 0; round < 6; round++) {
			List<StoryTools.StoryView> stories = StoryTools.within(bank, storiesMax);
			List<String> storyTitles = (kind == AiRequestKind.WRITE_STORIES) ? this.stories.titles(userId)
					: bank.subList(stories.size(), bank.size()).stream().map(StoryTools.StoryView::title).toList();
			// An interview's bank bigger than a request hands out: the steps say where the rest is
			// Every call of it names the profile: one the assistant's own configuration names may be another
			String instructions = "Pass profile_id " + userId + " to every JobWarden tool you call for it.\n"
					+ ((kind == AiRequestKind.PREPARE_INTERVIEW && !storyTitles.isEmpty())
							? RequestInstructions.of(request) + "\nstory_titles are more stories, which list_stories gives in full."
							: RequestInstructions.of(request));
			CvTools.CvView cv = cv(userId, request, fileTextMax, fieldTextMax);
			details = new RequestDetails(request.id(), kind, instructions, requirements(request), taste, followed,
					companies, watched, recipients, searches, toScore, stories, storyTitles, baseLetter,
					(job != null) ? JobTools.details(job, descriptionMax) : null, cv);
			int excess = results().writeValueAsString(details).length() - REQUEST_MAX_LENGTH;
			if (excess <= 0) {
				break;
			}
			int storyLength = stories.stream().mapToInt(StoryTools::length).sum();
			int fileTextLength = (cv != null && cv.file() != null && cv.file().text() != null) ? cv.file().text().length()
					: 0;
			if (storyLength > 0) {
				storiesMax = Math.max(0, storyLength - excess);
			} else if (fileTextMax > LEAST_TEXT && fileTextLength > LEAST_TEXT) {
				fileTextMax = Math.max(LEAST_TEXT, Math.min(fileTextMax, fileTextLength) - excess);
			} else if (fieldTextMax > CvTools.LEAST_FIELD_TEXT && CvTools.longestFieldText(cv) > CvTools.LEAST_FIELD_TEXT) {
				fieldTextMax = CvTools.fieldTextMax(cv, excess);
			} else if (Math.min(descriptionMax, descriptionLength) > LEAST_TEXT) {
				descriptionMax = Math.max(LEAST_TEXT, Math.min(descriptionMax, descriptionLength) - excess);
			} else {
				// Nothing long is left to cut
				break;
			}
		}
		return Objects.requireNonNull(details, "the first round always makes the details");
	}

	private JsonMapper results() {
		JsonMapper mapper = this.results;
		if (mapper == null) {
			mapper = McpController.results(this.json);
			this.results = mapper;
		}
		return mapper;
	}

	/**
	 * The CV the steps need: full for what's written from what the person did, brief for knowing who they are; none for
	 * requests that need none, or only Auto-apply's own reading of it.
	 */
	private CvTools.@Nullable CvView cv(long userId, AiRequest request, int fileTextMax, int fieldTextMax) {
		return switch (request.kind()) {
			case TAILOR_CV -> this.cvs.viewForRequest(userId, CvTools.Depth.FULL, fileTextMax, fieldTextMax);
			case PREPARE_INTERVIEW, SCORE_JOB, SCORE_JOBS, WRITE_COVER_LETTER, WRITE_STORIES ->
				this.cvs.viewForRequest(userId, CvTools.Depth.POINTS, fileTextMax, fieldTextMax);
			case FIND_JOBS_FOR_CV, WRITE_PROJECTS, WRITE_OUTREACH, NEGOTIATE_OFFER ->
				this.cvs.viewForRequest(userId, CvTools.Depth.BRIEF, fileTextMax, fieldTextMax);
			case FIND_JOBS -> request.requirements().useCv()
					? this.cvs.viewForRequest(userId, CvTools.Depth.BRIEF, fileTextMax, fieldTextMax) : null;
			default -> null;
		};
	}

	/** The profile's base letter, filled in for the job: the letter the assistant starts from. */
	private @Nullable String baseLetter(long userId, @Nullable ApplicationDetails job) {
		return this.letters.findBaseLetter(userId)
			.map(base -> (job == null) ? base.body() : base.fillIn(job.company(), job.title()))
			.orElse(null);
	}

	/** The jobs found for the profile that it decided on last: those it saved or applied to, and those it discarded. */
	private Taste taste(long userId) {
		List<FoundJob> decided = this.discovery.findDecided(userId, 3 * TASTE);
		return new Taste(
				decided.stream().filter(job -> job.decision() != Decision.DISCARDED).limit(TASTE).map(RequestTools::name).toList(),
				decided.stream().filter(job -> job.decision() == Decision.DISCARDED).limit(TASTE).map(RequestTools::name).toList());
	}

	/**
	 * The people to write to about a job, each once, with why: its contact, the recruiters the profile has for it or
	 * at its company, and the authors of the posts it came from, or about its company.
	 */
	private List<RecipientRef> recipients(long userId, ApplicationDetails job) {
		long jobId = job.id();
		Map<String, RecipientRef> found = new LinkedHashMap<>();
		FollowUp contact = job.followUp();
		String contactName = contact.contactName();
		if (contactName != null) {
			add(found, new RecipientRef(contactName, contact.contactRole(), contact.contactLink(),
					contact.contactEmail(), "the job's contact"));
		}
		String company = job.company();
		for (Recruiter recruiter : this.hiring.findRecruiters(userId)) {
			boolean forTheJob = recruiter.contactFor().stream().anyMatch(contactJob -> contactJob.applicationId() == jobId);
			if (forTheJob || (company != null && company.equalsIgnoreCase(recruiter.company()))) {
				add(found, new RecipientRef(recruiter.name(), recruiter.headline(), recruiter.profileUrl(), null,
						forTheJob ? "the job's contact on the Who's hiring page" : "recruits at " + company));
			}
		}
		if (company != null) {
			this.hiring.findPosts(userId, HiringPostQuery.builder().search(company).build())
				.stream()
				.filter(post -> Objects.equals(post.applicationId(), jobId) || company.equalsIgnoreCase(post.company()))
				.forEach(post -> add(found, new RecipientRef(post.recruiter().name(), post.recruiter().headline(),
						post.recruiter().profileUrl(), null, "posted that " + company + " is hiring: " + post.link())));
		}
		return found.values().stream().limit(RECIPIENTS).toList();
	}

	/**
	 * Each person once: the same profile, or the same name when either has no profile. What one record lacks, the other
	 * fills in (the job's contact's email, a recruiter's profile); the first reason stays.
	 */
	private static void add(Map<String, RecipientRef> found, RecipientRef recipient) {
		for (Map.Entry<String, RecipientRef> known : found.entrySet()) {
			RecipientRef kept = known.getValue();
			boolean sameProfile = kept.profileLink() != null && recipient.profileLink() != null
					&& kept.profileLink().equalsIgnoreCase(recipient.profileLink());
			boolean sameName = (kept.profileLink() == null || recipient.profileLink() == null)
					&& nameKey(kept.name()).equals(nameKey(recipient.name()));
			if (sameProfile || sameName) {
				known.setValue(new RecipientRef(kept.name(), either(kept.role(), recipient.role()),
						either(kept.profileLink(), recipient.profileLink()), either(kept.email(), recipient.email()),
						kept.why()));
				return;
			}
		}
		found.put(String.valueOf(found.size()), recipient);
	}

	/** The first value, or else the second: either may be missing, both too. */
	private static @Nullable String either(@Nullable String first, @Nullable String second) {
		return (first != null) ? first : second;
	}

	/** A name as written in either record: whatever the case and the spaces. */
	private static String nameKey(String name) {
		return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static String name(FoundJob job) {
		return (job.company() != null) ? job.title() + " at " + job.company() : job.title();
	}

	/** The companies of the jobs still in play, those changed last first. */
	private List<String> companies(long userId) {
		return Stream.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEWING)
			.flatMap(status -> this.applications.find(userId, ApplicationQuery.builder()
				.status(status)
				.sort(ApplicationSort.MODIFIED)
				.direction(Sort.Direction.DESC)
				.page(0)
				.size(COMPANIES)
				.build()).getContent().stream())
			.map(ApplicationSummary::company)
			.filter(Objects::nonNull)
			.distinct()
			.limit(COMPANIES)
			.toList();
	}

	private static @Nullable RequirementsView requirements(AiRequest request) {
		if (!request.kind().hasRequirements()) {
			return null;
		}
		AiRequirements asked = request.requirements();
		return new RequirementsView(asked.titles(), asked.locations(), asked.skills(), asked.excludes(),
				asked.workTypes().stream().map(WorkType::name).sorted().toList(), asked.seniority(), asked.minSalary(),
				asked.recency().name(), asked.notes(), asked.useCv() || request.kind() == AiRequestKind.FIND_JOBS_FOR_CV,
				asked.maxJobs());
	}

	private static RequestRow row(AiRequest request) {
		return new RequestRow(request.id(), request.kind(), request.status(), request.summary(), request.foundJobs(),
				request.createdAt(), request.finishedAt());
	}

	record RequestRow(long id, AiRequestKind kind, AiRequestStatus status, @Nullable String summary, long jobsFound,
			Instant createdAt, @Nullable Instant finishedAt) {
	}

	/**
	 * A request, its steps first: the data comes after, the job and the CV, the longest, last.
	 *
	 * @param taste for finding jobs: what the profile kept and discarded of those found for it
	 * @param followed for hiring posts: the recruiters the profile follows
	 * @param companies for recruiters: the companies of the profile's jobs still in play
	 * @param watchedCompanies for finding jobs: the companies whose boards JobWarden reads itself
	 * @param recipients for a job's messages: the people to write to, with why
	 * @param savedSearches for running them: each with its queries
	 * @param jobsToScore for scoring: the jobs with no score yet
	 * @param stories for an interview: the story bank
	 * @param storyTitles for drafting stories: those the bank has
	 * @param baseLetter for a cover letter: the person's own, filled in for the job
	 * @param job for a request about one job: the job, as get_job gives it
	 * @param cv for what's written from the CV: the CV, as get_cv gives it
	 */
	record RequestDetails(long requestId, AiRequestKind kind, String instructions,
			@Nullable RequirementsView requirements, @Nullable Taste taste, List<RecruiterRef> followed,
			List<String> companies, List<String> watchedCompanies, List<RecipientRef> recipients,
			List<DiscoverTools.SearchView> savedSearches, List<FitTools.JobRef> jobsToScore,
			List<StoryTools.StoryView> stories, List<String> storyTitles, @Nullable String baseLetter,
			JobTools.@Nullable JobDetails job, CvTools.@Nullable CvView cv) {
	}

	/**
	 * @param role what they do, e.g. Technical Recruiter at Acme
	 * @param why how they're tied to the job, e.g. the job's contact
	 */
	record RecipientRef(String name, @Nullable String role, @Nullable String profileLink, @Nullable String email,
			String why) {
	}

	/**
	 * @param kept the jobs found for it that the profile saved or applied to, the latest first, e.g. Java Developer at
	 * Acme
	 * @param discarded those it discarded
	 */
	record Taste(List<String> kept, List<String> discarded) {
	}

	record RecruiterRef(String name, String profileUrl) {
	}

	/**
	 * @param recency how recent the postings must be: ANY, DAY, WEEK, MONTH or YEAR
	 * @param useCv whether to match the jobs against the CV (get_cv)
	 */
	record RequirementsView(List<String> titles, List<String> locations, List<String> skills, List<String> excludes,
			List<String> workTypes, @Nullable String seniority, @Nullable String minSalary, String recency,
			@Nullable String notes, boolean useCv, int maxJobs) {
	}

}
