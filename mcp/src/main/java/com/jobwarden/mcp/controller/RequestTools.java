package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.search.WorkType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.bool;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;

/**
 * What the profile asked Claude to do from the AI Assistant page: the requests waiting, each with step-by-step
 * instructions, and finishing them. The page shows each request's status and summary, and the jobs found for it.
 */
@Component
@RequiredArgsConstructor
class RequestTools implements McpToolSet {

	private static final int SUMMARY_MAX_LENGTH = 2000;

	/** How long after applying an application with no news counts as No response, and when to follow up before. */
	static final int NO_RESPONSE_AFTER_DAYS = 30;

	static final int FOLLOW_UP_AFTER_DAYS = 7;

	private final Profiles profiles;

	private final DiscoveryService discovery;

	@Override
	public List<McpTool> tools() {
		return List.of(listRequests(), getRequest(), finishRequest());
	}

	private McpTool listRequests() {
		return new McpTool("list_requests", "List requests",
				"What the profile asked Claude to do from JobWarden and is still waiting for, the oldest first: find jobs for "
						+ "the CV or for requirements, run the saved searches, check the postings, tidy up applications with no "
						+ "answer, tailor the CV to a job, find hiring posts or recruiters, apply to jobs. Do each with "
						+ "get_request, which says how.",
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
					return new RequestDetails(request.id(), request.kind(), request.status(), request.applicationId(),
							requirements(request), instructions(request), request.createdAt());
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
				Call list_found_jobs and list_jobs to see what the profile already found, saved, applied to or discarded: \
				skip those, and learn what they like and don't.
				Search the web for open postings: companies' career pages (Lever, Greenhouse, Ashby, Workable, \
				SmartRecruiters...), LinkedIn, and the job boards of the countries asked for. Prefer recent postings.
				Open each promising posting. Keep only real postings that are still open and fit; check_postings with a \
				url helps when you're unsure.
				Add them with add_found_jobs and request_id %d, at most %d jobs, each with its own link and a note on why \
				it fits, in a sentence: the profile reads it when deciding.
				""".formatted(id, max);
		String steps = switch (request.kind()) {
			case FIND_JOBS_FOR_CV -> """
					Call get_cv and read the headline, the experience, the skills and where the person lives.
					From the CV and the requirements (they win where they say more), decide on the job titles, the \
					seniority and where to look.
					""" + findAndAdd;
			case FIND_JOBS -> (request.requirements().useCv() ? "Call get_cv: match the jobs against the CV too.\n" : "")
					+ "Look for jobs that fit the requirements.\n" + findAndAdd;
			case RUN_SAVED_SEARCHES -> """
					Call list_saved_searches. Run each search's queries with your web search, and open the results.
					Call list_found_jobs to skip what the profile already has.
					Add the real, open postings with add_found_jobs, with the search's search_id and request_id %d, at \
					most %d jobs in all, each with a note on why it fits.
					""".formatted(id, max);
			case CHECK_POSTINGS -> """
					Call check_postings with close_when_gone true: it checks the Saved, Applied and Interviewing jobs, and \
					closes the Saved and Applied ones whose posting is certainly gone.
					Open the UNKNOWN ones yourself if you can, and move those whose posting you saw gone to \
					POSITION_CLOSED with update_job_status, saying why in the note.
					In the summary: how many you checked, closed, found still open, and couldn't tell.
					""";
			case FOLLOW_UP -> """
					Call list_jobs with status APPLIED and limit 200, then get_job for each to read its timeline and its \
					follow-up.
					A job applied to %d days ago or more, with nothing on its timeline since: move it to NO_RESPONSE with \
					update_job_status, with a note that the company never answered.
					A newer one without a follow_up_on day: set it with update_follow_up, %d days after the application, \
					or tomorrow when that's past.
					In the summary: how many you moved and how many follow-ups you set.
					""".formatted(NO_RESPONSE_AFTER_DAYS, FOLLOW_UP_AFTER_DAYS);
			case TAILOR_CV -> tailorCv(request.applicationId());
			case FIND_HIRING_POSTS -> """
					Call list_recruiters: the people the profile follows come first. Look at their recent posts too, on \
					their profile's activity page (the profile link with /recent-activity/all/ after it).
					Call list_hiring_posts to skip the posts the profile already has.
					Search the web for posts by recruiters and hiring managers saying they're hiring for the requirements: \
					e.g. site:linkedin.com/posts ("hiring" OR "we're hiring" OR "join our team") with the titles, skills \
					and places; X and Reddit hiring threads too. Only posts from the time the requirements give, else \
					from the last month.
					Open each. Keep only real posts about open roles: not job seekers, courses or ads for a service.
					Add them with add_hiring_posts, at most %d: each with its link, the day it was posted, its text as \
					posted (shortened when long, never rewritten), the role and company it's for, the country, and its \
					author: name, headline and profile link.
					In the summary: how many posts you added, from how many people.
					""".formatted(max);
			case FIND_RECRUITERS -> """
					Call list_recruiters to skip the people the profile already has.
					Call list_jobs with status SAVED, then APPLIED and INTERVIEWING: the companies there come first. Find \
					the recruiters and talent acquisition people who hire for those teams.
					Search the web for recruiters, talent partners and hiring managers who hire for the requirements' \
					roles and skills in their places, at companies and at agencies: e.g. site:linkedin.com/in \
					("IT Recruiter" OR "Technical Recruiter" OR "Talent Acquisition") Java Romania; the companies' \
					career and team pages too.
					Open each profile. Keep real people who recruit for these roles now; not job seekers, not people who \
					left recruiting.
					Add them with add_recruiters, at most %d: each with name, headline, profile link, company, country, \
					and a note on why they're worth following, in a sentence.
					In the summary: how many you added, and at which companies.
					""".formatted(max);
			case AUTO_APPLY -> AUTO_APPLY_STEPS;
		};
		return "Request " + id + " from JobWarden: " + describe(request.kind()) + ".\n" + steps + finish;
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
		};
	}

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
				Call get_cv for the CV: the fields filled in, and the text of the file uploaded. Call get_job with \
				job_id %1$d for the posting; when it has no description, open its link to read it.
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
	 */
	record RequestDetails(long requestId, AiRequestKind kind, AiRequestStatus status, @Nullable Long jobId,
			@Nullable RequirementsView requirements, String instructions, Instant createdAt) {
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
