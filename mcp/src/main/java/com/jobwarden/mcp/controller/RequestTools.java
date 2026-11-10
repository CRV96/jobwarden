package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.WorkType;
import com.jobwarden.watchlist.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.bool;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * What the profile asked Claude to do from the AI Assistant page: the requests waiting, each with step-by-step
 * instructions, and finishing them. The page shows each request's status and summary, and the jobs found for it.
 * <p>
 * A request comes with what its steps need to know, in a few lines (what the profile kept and discarded, the
 * recruiters it follows, the companies of its jobs), so Claude needn't list it all; and the steps lean on what costs
 * Claude the least: the server leaving out what the profile has, filter_links before opening search results, WebFetch
 * with a question rather than whole pages, check_postings for many postings at once.
 */
@Component
@RequiredArgsConstructor
class RequestTools implements McpToolSet {

	private static final int SUMMARY_MAX_LENGTH = 2000;

	/** How long after applying an application with no news counts as No response, and when to follow up before. */
	static final int NO_RESPONSE_AFTER_DAYS = 30;

	static final int FOLLOW_UP_AFTER_DAYS = 7;

	/** How much of each the request brings along: enough to go by, little enough to read at a glance. */
	private static final int TASTE = 10;

	private static final int FOLLOWED = 25;

	private static final int COMPANIES = 20;

	private static final int WATCHED_COMPANIES = 50;

	/**
	 * What the steps of finding jobs say about the watchlist, which JobWarden reads for the saved searches with no
	 * tokens.
	 */
	private static final String WATCHED = """
			watched_companies lists the companies whose job boards JobWarden reads itself, every few hours, for the saved \
			searches: their matching postings are on the AI Assistant page already, so look elsewhere first.
			When you come across a company on Greenhouse, Lever or Ashby whose jobs fit, call watch_company with a link to \
			its board, so JobWarden watches it from then on, with no tokens.
			""";

	private final Profiles profiles;

	private final DiscoveryService discovery;

	private final HiringService hiring;

	private final ApplicationService applications;

	private final WatchlistService watchlist;

	@Override
	public List<McpTool> tools() {
		return List.of(listRequests(), getRequest(), finishRequest());
	}

	private McpTool listRequests() {
		return new McpTool("list_requests", "List requests",
				"What the profile asked Claude to do from JobWarden and is still waiting for, the oldest first: find jobs for "
						+ "the CV or for requirements, run the saved searches, check the postings, tidy up applications with no "
						+ "answer, tailor the CV to a job, find hiring posts or recruiters, apply to jobs, sort the inbox, write "
						+ "the CV's projects. Do each with get_request, which says how.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"all", bool("Also the finished ones, the latest first; only those waiting or being done if left out"))),
				true, false, call -> {
					long userId = this.profiles.of(call);
					boolean all = call.arguments().path("all").asBoolean(false);
					List<AiRequest> requests = all ? this.discovery.findRequests(userId, 50) : this.discovery.findOpenRequests(userId);
					return Map.of("requests", requests.stream().map(RequestTools::row).toList());
				});
	}

	private McpTool getRequest() {
		return new McpTool("get_request", "Get a request",
				"One request from the AI Assistant page, with what was asked for and step-by-step instructions. Getting it "
						+ "marks it as being done, which the page shows; finish it with finish_request.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"request_id", integer("The request, from list_requests or the prompt")),
						"request_id"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					AiRequest request = this.discovery.startRequest(userId, call.requiredNumber("request_id"));
					return details(userId, request);
				});
	}

	private McpTool finishRequest() {
		return new McpTool("finish_request", "Finish a request",
				"Marks a request as done, or as failed, with a summary the profile reads on the AI Assistant page: what you "
						+ "did and found, or why you couldn't.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"request_id", integer("The request"),
						"summary", string("What you did, in a sentence or three: e.g. how many jobs you added and where from"),
						"done", bool("false when you couldn't do it; true if left out")),
						"request_id", "summary"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					String summary = call.requiredString("summary");
					AiRequest request = this.discovery.finishRequest(userId, call.requiredNumber("request_id"),
							call.arguments().path("done").asBoolean(true),
							(summary.length() <= SUMMARY_MAX_LENGTH) ? summary : summary.substring(0, SUMMARY_MAX_LENGTH - 1) + "…");
					return row(request);
				});
	}

	/**
	 * What to do, step by step, with the tools that do it.
	 */
	static String instructions(AiRequest request) {
		long id = request.id();
		int max = request.requirements().maxJobs();
		String finish = "Finally, call finish_request with request_id " + id + " and a short summary; with done false and "
				+ "the reason if you couldn't do it.";
		String findAndAdd = """
				Search the web for open postings: companies' career pages (Lever, Greenhouse, Ashby, Workable, \
				SmartRecruiters...), LinkedIn, and the job boards of the countries asked for. Prefer recent postings.
				Pass the results' links to filter_links, and keep only the new ones whose title and snippet fit. taste \
				lists jobs the profile kept and discarded: lean towards the first, away from the second.
				Read each promising posting with WebFetch and a short question (is it open; title, company, location, \
				salary, the must-haves), not the whole page; check_postings with urls tells open from closed for many.
				Add the real, open ones that fit with add_found_jobs and request_id %d, at most %d jobs, each with its \
				own link and a note on why it fits, in a sentence: the profile reads it when deciding.
				%s""".formatted(id, max, WATCHED);
		String steps = switch (request.kind()) {
			case FIND_JOBS_FOR_CV -> """
					Call get_cv and read the headline, the experience, the skills and where the person lives.
					From the CV and the requirements (they win where they say more), decide on the job titles, the \
					seniority and where to look.
					""" + findAndAdd;
			case FIND_JOBS -> (request.requirements().useCv() ? "Call get_cv: match the jobs against the CV too.\n" : "")
					+ "Look for jobs that fit the requirements.\n" + findAndAdd;
			case RUN_SAVED_SEARCHES -> """
					Call list_saved_searches. Run each search's queries with your web search.
					Pass the results' links to filter_links, and open only the new ones that look right, with WebFetch \
					and a short question.
					Add the real, open postings with add_found_jobs, with the search's search_id and request_id %d, at \
					most %d jobs in all, each with a note on why it fits.
					%s""".formatted(id, max, WATCHED);
			case CHECK_POSTINGS -> """
					Call check_postings with close_when_gone true: it checks the Saved, Applied and Interviewing jobs, and \
					closes the Saved and Applied ones whose posting is certainly gone.
					Open the UNKNOWN ones with WebFetch if you can, asking only whether the posting is still open, and \
					move those you saw gone to POSITION_CLOSED with update_job_status, saying why in the note.
					In the summary: how many you checked, closed, found still open, and couldn't tell.
					""";
			case FOLLOW_UP -> """
					Call tidy_applications: it moves the jobs applied to %d days ago or more, with nothing on their \
					timeline since, to NO_RESPONSE, and gives the newer ones without a day to follow up one.
					In the summary: what it did.
					""".formatted(NO_RESPONSE_AFTER_DAYS);
			case TAILOR_CV -> tailorCv(request.applicationId());
			case FIND_HIRING_POSTS -> """
					followed lists the people the profile follows: look at their recent posts first, on their profile's \
					activity page (the profile link with /recent-activity/all/ after it).
					Search the web for posts by recruiters and hiring managers saying they're hiring for the requirements: \
					e.g. site:linkedin.com/posts ("hiring" OR "we're hiring" OR "join our team") with the titles, skills \
					and places; X and Reddit hiring threads too. Only posts from the time the requirements give, else \
					from the last month.
					Pass the results' links to filter_links and keep the new ones. Judge each from its snippet; open it, \
					with WebFetch, only when the snippet doesn't say enough. Keep only real posts about open roles: not \
					job seekers, courses or ads for a service.
					Add them with add_hiring_posts, at most %d: each with its link, the day it was posted, its text as \
					posted (shortened when long, never rewritten), the role and company it's for, the country, and its \
					author: name, headline and profile link.
					In the summary: how many posts you added, from how many people.
					""".formatted(max);
			case FIND_RECRUITERS -> """
					companies lists the companies of the profile's Saved, Applied and Interviewing jobs: they come first. \
					Find the recruiters and talent acquisition people who hire for those teams.
					Search the web for recruiters, talent partners and hiring managers who hire for the requirements' \
					roles and skills in their places, at companies and at agencies: e.g. site:linkedin.com/in \
					("IT Recruiter" OR "Technical Recruiter" OR "Talent Acquisition") Java Romania; the companies' \
					career and team pages too.
					Pass the profiles' links to filter_links and keep the new ones. Judge each from its result's title \
					and snippet (name, headline, company); open one only when they don't say enough. Keep real people \
					who recruit for these roles now; not job seekers, not people who left recruiting.
					Add them with add_recruiters, at most %d: each with name, headline, profile link, company, country, \
					and a note on why they're worth following, in a sentence.
					In the summary: how many you added, and at which companies.
					""".formatted(max);
			case AUTO_APPLY -> AUTO_APPLY_STEPS;
			case PREPARE_INTERVIEW -> prepareInterview(request.applicationId());
			case SCORE_JOB -> "Call get_cv with full true, then score job " + request.applicationId() + ".\n" + SCORING_STEPS;
			case SCORE_JOBS -> """
					Call get_cv with full true, then list_jobs_to_score with limit %d, and score each job.
					""".formatted(SCORE_AT_ONCE) + SCORING_STEPS + "In the summary: how many you scored, and the best fits.\n";
			case SORT_INBOX -> SORT_INBOX_STEPS;
			case WRITE_PROJECTS -> WRITE_PROJECTS_STEPS;
		};
		return "Request " + id + " from JobWarden: " + describe(request.kind()) + ".\n" + steps + finish;
	}

	/**
	 * The request, with its steps and what they need to know, so they needn't list it: what the profile kept and
	 * discarded when finding jobs, the recruiters it follows for hiring posts, its jobs' companies for recruiters.
	 */
	private RequestDetails details(long userId, AiRequest request) {
		Taste taste = switch (request.kind()) {
			case FIND_JOBS_FOR_CV, FIND_JOBS, RUN_SAVED_SEARCHES -> taste(userId);
			default -> null;
		};
		List<RecruiterRef> followed = (request.kind() == AiRequestKind.FIND_HIRING_POSTS) ? this.hiring
			.findRecruiters(userId)
			.stream()
			.filter(Recruiter::followed)
			.limit(FOLLOWED)
			.map(recruiter -> new RecruiterRef(recruiter.name(), recruiter.profileUrl()))
			.toList() : List.of();
		List<String> companies = (request.kind() == AiRequestKind.FIND_RECRUITERS) ? companies(userId) : List.of();
		List<String> watched = (taste != null) ? this.watchlist.findCompanies(userId)
			.stream()
			.limit(WATCHED_COMPANIES)
			.map(company -> company.name() + " (" + company.boardUrl() + ")")
			.toList() : List.of();
		return new RequestDetails(request.id(), request.kind(), request.status(), request.applicationId(),
				requirements(request), instructions(request), taste, followed, companies, watched, request.createdAt());
	}

	/** The jobs found for the profile that it decided on last: those it saved or applied to, and those it discarded. */
	private Taste taste(long userId) {
		List<FoundJob> decided = this.discovery.findDecided(userId, 3 * TASTE);
		return new Taste(
				decided.stream().filter(job -> job.decision() != Decision.DISCARDED).limit(TASTE).map(RequestTools::name).toList(),
				decided.stream().filter(job -> job.decision() == Decision.DISCARDED).limit(TASTE).map(RequestTools::name).toList());
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

	private static String describe(AiRequestKind kind) {
		return switch (kind) {
			case FIND_JOBS_FOR_CV -> "find jobs that match the profile's CV";
			case FIND_JOBS -> "find jobs that match the requirements";
			case RUN_SAVED_SEARCHES -> "find new jobs for the saved searches";
			case CHECK_POSTINGS -> "check which postings of the jobs still waiting are gone";
			case FOLLOW_UP -> "tidy up the applications that got no answer";
			case TAILOR_CV -> "tailor the profile's CV to one job";
			case FIND_HIRING_POSTS -> "find recruiters' posts saying they're hiring, for the Who's hiring page";
			case FIND_RECRUITERS -> "find recruiters to follow, for the Who's hiring page";
			case AUTO_APPLY -> "apply to the jobs queued on the Auto-apply page, in the profile's browser";
			case PREPARE_INTERVIEW -> "prepare the profile for one job's interview, with a brief about the company and the role";
			case SCORE_JOB -> "score how well the profile fits one job";
			case SCORE_JOBS -> "score how well the profile fits the jobs with no score yet";
			case SORT_INBOX -> "sort the updates found in the profile's emails, waiting on the Inbox page";
			case WRITE_PROJECTS -> "write the CV's personal projects from the profile's GitHub repositories";
		};
	}

	/**
	 * Claude finds the job each email is about where JobWarden couldn't, and checks what JobWarden thought it says;
	 * what it isn't sure of stays for the profile.
	 */
	private static final String SORT_INBOX_STEPS = """
			Call list_email_updates with limit 50: the emails waiting, each with what JobWarden thinks it says, the words \
			that said it, the start of the email, and the job it matched, if any.
			For each, find the job it's about: the matched one when the email names its company or its role; else \
			list_jobs with search set to the company (from the sender's domain, the signature or the text), and the job \
			whose title the email names, or the only one at that company.
			Apply it with apply_email_update when you're sure of the job and of what the email says: an invitation to \
			an interview or a call is INTERVIEWING, a confirmation that the application arrived APPLIED, a no REJECTED, \
			an offer OFFER. What JobWarden thought may be wrong.
			Dismiss with dismiss_email_update what isn't news about an application: job alerts, newsletters, ads, \
			messages about other roles.
			Leave the ones you aren't sure of: the profile decides them on the Inbox page. Never apply an email to a job \
			it isn't about.
			In the summary: how many you applied (and as what), dismissed and left, and why you left them, e.g. no job \
			at that company.
			""";

	/** How many projects a CV shows at most: the ones worth it. */
	static final int PROJECTS_MAX = 6;

	/** Claude writes from what the repositories show, never more. */
	private static final String WRITE_PROJECTS_STEPS = """
			Call get_cv for the headline, the skills, and the projects the CV has now.
			Call list_github_projects: the repositories the profile chose on the CV page, each with its description, \
			languages, topics, stars, dates and the start of its README.
			Write the CV's personal projects from them, the ones most worth showing first, at most %d: each with its \
			name, its link, the technologies in a few words, and in one to three sentences what it does and what the \
			person built in it, from the README and the languages, with numbers when the README gives them. Never claim \
			what the repository doesn't show. Leave out forks with nothing of the person's own, and empty ones.
			Save them with save_cv_projects: they take the place of the CV's projects, which the profile then reviews on \
			the CV page.
			In the summary: the projects you wrote, and those you left out and why.
			""".formatted(PROJECTS_MAX);

	/** How many jobs one request scores: the CV is read once for them all. */
	static final int SCORE_AT_ONCE = 15;

	/**
	 * Claude reads the requirements; JobWarden works the score out from them, so scores compare across jobs and with
	 * how the applications went.
	 */
	private static final String SCORING_STEPS = """
			For a job: get_job, for the posting; when it has no description, read its link with WebFetch, asking for the \
			requirements alone.
			List what the posting asks for, from %d to %d requirements, in its own words, short: the skills, tools, \
			years, languages and duties that matter, and the place, the level and the work type when it sets them. Each \
			is MUST when the posting requires it (required, must, you have, you are) and NICE when it's a plus (nice to \
			have, bonus, preferred, ideally).
			For each, judge the CV honestly: MET when the CV shows it, PARTIAL when it has something near it (a related \
			tool, fewer years, a smaller scale), MISSING when nothing supports it; a note of a few words on why. Never \
			count what the CV doesn't say.
			Save it with save_job_fit, with a summary in a sentence or two: JobWarden works out the score from the \
			requirements, from 1 to 5.
			""".formatted(FitTools.REQUIREMENTS_MIN, FitTools.REQUIREMENTS_MAX);

	/**
	 * Applying in the profile's own browser, with its sign-ins: never around a CAPTCHA, never on a site whose terms
	 * forbid it, and never with an answer made up.
	 */
	private static final String AUTO_APPLY_STEPS = """
			You need a browser you drive in the profile's own Chrome, with its sign-ins (Claude in Chrome). Without one, \
			finish the request with done false and say so.
			Call get_application_answers: the contact details and the answers to use. Call list_apply_queue for the \
			jobs: do them in order, each with start_apply first.
			For a job without a tailored CV (has_tailored_cv false), tailor one first: get_cv, get_job, then \
			save_tailored_cv, truthfully, in the posting's words. Download the job's CV from its cv_url to a file, to \
			upload.
			Open the job's link and its application form. Report SKIPPED, with why, for LinkedIn Easy Apply and any site \
			whose terms forbid applying automatically, a form behind a sign-in the profile doesn't have, or a posting \
			that's closed.
			Spare the context in the browser: find the form's fields with find and read_page (interactive elements \
			only), fill them with form_input, and take a screenshot only to see a step that went wrong.
			Fill the form in: the contact details and the CV from JobWarden; every question from the answers, as they \
			are; a cover letter only when cover_letter is WRITE or the form needs one, short, for this job, from the CV; \
			the voluntary questions (gender, ethnicity, disability, veteran) declined when decline_voluntary is true. \
			Write answers to open questions, e.g. why this company, from the CV and the posting. Never make up a fact: a \
			question about the person that neither the answers nor the CV answers, e.g. a number, a date, a yes or no, \
			stops the job: leave the form, and report NEEDS_ANSWER with the question word for word.
			Never solve or get around a CAPTCHA or a bot check: ask the profile to solve it in the browser, and wait.
			Mode REVIEW: stop before sending, report FILLED, and tell the profile which tab to check and send. Mode \
			SUBMIT: send it, check that the site confirms it, and report APPLIED with the site and a note of what you \
			sent. A send that fails is FAILED, with why.
			In the summary: how many you applied to, filled in, left waiting for an answer, and skipped.
			""";

	/**
	 * A CV that gets through the screening software employers run CVs through, then reads well to a person: the
	 * posting's words for what the person really did, in plain sections.
	 */
	private static String tailorCv(@Nullable Long jobId) {
		return """
				Call get_cv with full true for the CV: the fields filled in, and the text of the file uploaded. Call \
				get_job with job_id %1$d for the posting; when it has no description, read its link with WebFetch.
				List what the posting asks for: skills, tools, certifications and duties, must-haves first, in its own \
				words, e.g. Spring Boot rather than Spring, and Amazon Web Services (AWS) spelled out once.
				Rewrite the CV for this job, truthfully: never add a skill, an employer, a title, a date, a degree or a \
				number the CV doesn't support. Reword, reorder and choose instead:
				- headline: the posting's title, when it's true of the person;
				- summary: three or four lines on why the person fits, with the most important keywords;
				- experience: the same jobs and dates, newest first; for each, three to six points, a line each in its \
				description, starting with a verb, naming the tools, with the results and numbers the CV has; what \
				matters for this job first, and what doesn't left out;
				- skills: the posting's keywords the person has first, in its words, then the other relevant ones, ten \
				to twenty in all;
				- education, languages and certifications as they are.
				Plain for the screening software: no tables, columns, icons or graphics; JobWarden lays the CV out in one \
				column, with standard section names. Leave out the contact details the CV page has: JobWarden keeps \
				them.
				Save it with save_tailored_cv and job_id %1$d, with matched_keywords (the posting's keywords the CV now \
				names), missing_keywords (those the posting asks for that the person can't honestly claim) and notes: \
				what you changed and what to check before sending it, in a few lines.
				""".formatted(jobId);
	}

	/**
	 * A brief read the evening before an interview: who the company is, what they'll ask, what to tell them, what to
	 * ask them. Found cheaply, from a few pages read with a question each.
	 */
	private static String prepareInterview(@Nullable Long jobId) {
		return """
				Call get_job with job_id %1$d for the posting, and get_cv with full true for what the person did.
				Find out about the company: search the web for its name with about, news, engineering blog and interview \
				questions (Glassdoor, Reddit, Blind). Read the three to six most useful results with WebFetch and a short \
				question each, not whole pages.
				Write the brief for someone reading it the evening before, short and to the point:
				- company: what they make and for whom, how big, where, how they're doing, and what's new this year;
				- role: what the job is really about, and what they'll look for, from the posting;
				- questions: eight to twelve questions likely asked, about the posting's tools and about the person, \
				those candidates report first;
				- stories: for what they'll look for, a story each from the CV, in a line or two, with the result; never \
				one the CV doesn't support;
				- ask_them: four to six good questions to ask them;
				- watch_out: what to look into before saying yes, e.g. layoffs, reviews, a vague salary; left out when \
				there's nothing;
				- sources: the addresses you read.
				Save it with save_interview_brief and job_id %1$d.
				""".formatted(jobId);
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
	 * @param jobId the job it's about, for a request about one
	 * @param taste for finding jobs: what the profile kept and discarded of those found for it
	 * @param followed for hiring posts: the recruiters the profile follows
	 * @param companies for recruiters: the companies of the profile's jobs still in play
	 * @param watchedCompanies for finding jobs: the companies whose boards JobWarden reads itself, with their boards
	 */
	record RequestDetails(long requestId, AiRequestKind kind, AiRequestStatus status, @Nullable Long jobId,
			@Nullable RequirementsView requirements, String instructions, @Nullable Taste taste,
			List<RecruiterRef> followed, List<String> companies, List<String> watchedCompanies, Instant createdAt) {
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
