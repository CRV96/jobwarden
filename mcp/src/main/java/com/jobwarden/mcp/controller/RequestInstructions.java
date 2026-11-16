package com.jobwarden.mcp.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import org.jspecify.annotations.Nullable;

/**
 * What a request asks the assistant to do, step by step, with the tools that do it: one set of steps for each kind of
 * request, worded for the assistant, which reads every character. RequestTools hands them out with the data they need.
 */
final class RequestInstructions {

	private RequestInstructions() {
	}

	/** What the steps of finding jobs say about the watchlist, which JobWarden reads for the saved searches with no tokens. */
	private static final String WATCHED = """
			JobWarden reads watched_companies' boards itself: look elsewhere. When you find a company on Greenhouse, \
			Lever or Ashby whose jobs fit, call watch_company with its board's link.
			""";

	/** In a request made from a saved search, its notes start with its id: (search_id 5) From the saved search Java. */
	private static final Pattern SAVED_SEARCH = Pattern.compile("^\\(search_id (\\d+)\\)");

	/**
	 * For a request made from a saved search: the search to add the jobs to, so they show under it.
	 */
	private static String savedSearch(AiRequest request) {
		String notes = request.requirements().notes();
		Matcher search = (notes == null) ? null : SAVED_SEARCH.matcher(notes);
		return (search != null && search.find())
				? "\nPass search_id " + search.group(1) + " to add_found_jobs too: they then show under that saved search."
				: "";
	}

	/**
	 * What to do, step by step, with the tools that do it. The data the request hands out with them (job, cv, stories...)
	 * the steps name as it's named there.
	 */
	static String of(AiRequest request) {
		long id = request.id();
		int max = request.requirements().maxJobs();
		String finish = "Finally, call finish_request with request_id " + id + " and a short summary; done false and why "
				+ "if you couldn't.";
		String findAndAdd = """
				Search the web for open postings: companies' career pages (Lever, Greenhouse, Ashby, Workable, \
				SmartRecruiters...), LinkedIn, and the job boards of the countries asked for; recent ones first.
				Pass the results' links to filter_links; keep the new ones whose title and snippet fit, leaning towards \
				taste.kept and away from taste.discarded.
				Open each promising posting with a short question (open? title, company, location, salary, must-haves), \
				not whole; check_postings with urls checks many at once.
				Add the real, open ones that fit with add_found_jobs and request_id %d, at most %d, each with its own link \
				and a one-sentence note on why it fits: the profile reads it when deciding.
				%s""".formatted(id, max, WATCHED);
		String steps = switch (request.kind()) {
			case FIND_JOBS_FOR_CV -> """
					From cv (headline, experience, skills, where the person lives) and the requirements, which win where \
					they say more, decide the titles, the seniority and where to look.
					""" + findAndAdd;
			case FIND_JOBS -> (request.requirements().useCv() ? "Look for jobs that fit the requirements and cv.\n"
					: "Look for jobs that fit the requirements.\n") + findAndAdd + savedSearch(request);
			case RUN_SAVED_SEARCHES -> """
					Run each of saved_searches' queries with your web search.
					Pass the results' links to filter_links; open only the new ones that look right, with a short question.
					Add the real, open postings with add_found_jobs, with the search's search_id and request_id %d, at \
					most %d in all, each with a one-sentence note on why it fits.
					%s""".formatted(id, max, WATCHED);
			case CHECK_POSTINGS -> """
					Call check_postings with close_when_gone true: it checks the Saved, Applied and Interviewing jobs, and \
					closes the Saved and Applied ones certainly gone.
					Open the UNKNOWN ones if you can, asking only whether the posting is open; move those gone to \
					POSITION_CLOSED with update_job_status, saying why in the note.
					In the summary: how many you checked, closed, found open, and couldn't tell.
					""";
			case FOLLOW_UP -> "Call tidy_applications. In the summary: what it did.\n";
			case TAILOR_CV -> tailorCv(request.applicationId());
			case FIND_HIRING_POSTS -> """
					followed lists people the profile follows: look at their recent posts first (their profile link with \
					/recent-activity/all/ after it).
					Search the web for posts by recruiters and hiring managers saying they're hiring for the requirements: \
					e.g. site:linkedin.com/posts ("hiring" OR "we're hiring" OR "join our team") with the titles, skills \
					and places; X and Reddit hiring threads too. Only posts from the requirements' time, else the last month.
					Pass the links to filter_links; judge the new ones by their snippet, opening one only when it says too \
					little. Keep real posts about open roles: not job seekers, courses or ads for a service.
					Add them with add_hiring_posts, at most %d: link, day posted, text as posted (shortened if long, never \
					rewritten), role, company, country, and author (name, headline, profile link).
					In the summary: how many posts, from how many people.
					""".formatted(max);
			case FIND_RECRUITERS -> """
					companies lists the companies of the profile's Saved, Applied and Interviewing jobs: find their \
					recruiters and talent acquisition people first.
					Search the web for recruiters, talent partners and hiring managers who hire for the requirements' \
					roles and skills in their places, at companies and agencies: e.g. site:linkedin.com/in ("IT Recruiter" \
					OR "Technical Recruiter" OR "Talent Acquisition") Java Romania; career and team pages too.
					Pass the profile links to filter_links; judge the new ones by their result's title and snippet, opening \
					one only when they say too little. Keep real people who recruit for these roles now.
					Add them with add_recruiters, at most %d: name, headline, profile link, company, country, and a \
					one-sentence note on why they're worth following.
					In the summary: how many, and at which companies.
					""".formatted(max);
			case AUTO_APPLY -> AUTO_APPLY_STEPS;
			case PREPARE_INTERVIEW -> prepareInterview(request.applicationId());
			case SCORE_JOB -> "job is the posting, cv the full CV.\n" + SCORING_STEPS;
			case SCORE_JOBS -> """
					cv is the full CV, jobs_to_score the jobs to score: get_job each.
					""" + SCORING_STEPS + "In the summary: how many you scored, and the best fits.\n";
			case SORT_INBOX -> SORT_INBOX_STEPS;
			case WRITE_PROJECTS -> WRITE_PROJECTS_STEPS;
			case WRITE_COVER_LETTER -> writeCoverLetter(request.applicationId());
			case WRITE_OUTREACH -> writeOutreach(request.applicationId());
			case NEGOTIATE_OFFER -> negotiateOffer(request.applicationId());
			case WRITE_STORIES -> WRITE_STORIES_STEPS;
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
			case PREPARE_INTERVIEW -> "prepare the profile for one job's interview, with a brief about the company and the role";
			case SCORE_JOB -> "score how well the profile fits one job";
			case SCORE_JOBS -> "score how well the profile fits the jobs with no score yet";
			case SORT_INBOX -> "sort the updates found in the profile's emails, waiting on the Inbox page";
			case WRITE_PROJECTS -> "write the CV's personal projects from the profile's GitHub repositories";
			case WRITE_COVER_LETTER -> "write a cover letter for one job";
			case WRITE_OUTREACH -> "write a LinkedIn note and an email about one job to the people who hire for it";
			case NEGOTIATE_OFFER -> "write a brief to negotiate one job's offer, with the market salaries JobWarden has";
			case WRITE_STORIES -> "draft stories for the profile's story bank, the STAR way, from its CV";
		};
	}

	/**
	 * The assistant finds the job each email is about where JobWarden couldn't, and checks what JobWarden thought it
	 * says; what it isn't sure of stays for the profile.
	 */
	private static final String SORT_INBOX_STEPS = """
			Call list_email_updates with limit 50: the emails waiting, each with JobWarden's guess, the words behind it, \
			the email's start, and the job it matched, if any.
			For each, find its job: the matched one when the email names its company or role; else list_jobs with search \
			set to the company (from the sender's domain, the signature or the text), and the job whose title the email \
			names, or the company's only one.
			When sure of the job and the news, apply it with apply_email_update: an invitation to an interview or a call \
			is INTERVIEWING, a confirmation that the application arrived APPLIED, a no REJECTED, an offer OFFER. \
			JobWarden's guess may be wrong.
			Dismiss with dismiss_email_update what isn't news about an application: job alerts, newsletters, ads, other \
			roles.
			Leave the ones you aren't sure of for the profile. Never apply an email to a job it isn't about.
			In the summary: how many you applied (and as what), dismissed and left, and why you left them.
			""";

	/** How many projects a CV shows at most: the ones worth it. */
	static final int PROJECTS_MAX = 6;

	/** The assistant writes from what the repositories show, never more. */
	private static final String WRITE_PROJECTS_STEPS = """
			cv has the headline, the skills and the projects the CV has now.
			Call list_github_projects: the repositories the profile chose, with description, languages, topics, stars, \
			dates and the start of the README.
			Write the CV's projects from them, the best first, at most %d: name, link, technologies in a few words, and \
			1-3 sentences on what it does and what the person built, from the README and the languages, with its \
			numbers. Never claim what the repository doesn't show; leave out forks with nothing of the person's, and \
			empty ones.
			Save them with save_cv_projects: they replace the CV's projects, for the profile to review.
			In the summary: the projects you wrote, and those you left out and why.
			""".formatted(PROJECTS_MAX);

	/**
	 * A cover letter a person would write: specific to the job and the company, from what the CV says, short enough
	 * to be read.
	 */
	private static String writeCoverLetter(@Nullable Long jobId) {
		return """
				job is the posting and its contact (without a description, read its link, asking for the role, the duties \
				and the requirements; cut short, read the rest where description_with says); cv is the full CV.
				When the posting doesn't say what the company makes, find out with one web search.
				base_letter, when there is one, is the person's own letter: keep its voice, its structure and the \
				paragraphs it always has, change only what this job needs, write it in the posting's language, and fill \
				in anything in braces, like {company}. Without one, write it in the posting's language, 250-400 words, in \
				four short paragraphs:
				- why they write: the role by its title, and what draws them to this company, specific, never flattery;
				- two or three of the posting's must-haves, each met by something from the CV, with its result and numbers;
				- what else they bring that the posting values: a tool, a domain, a language, how they work;
				- the close: glad to talk, and thanks.
				Start with Dear and the contact's or hiring manager's name when the posting gives it, else Dear hiring \
				team; end with a sign-off like Kind regards, without the name: JobWarden adds the CV page's name and \
				contact details.
				Plain and specific: no clichés (I am writing to express my interest, passionate, team player), nothing the \
				CV doesn't support, nothing made up about the company.
				Save it with save_cover_letter and job_id %1$d, with notes: what you highlighted, and what to check or \
				add before sending it.
				""".formatted(jobId);
	}

	/**
	 * Short messages a recruiter answers: who the person is, the role, one fact that fits, a light ask. The profile
	 * sends them itself.
	 */
	private static String writeOutreach(@Nullable Long jobId) {
		return """
				job is the posting; cv says who the person is: the headline, the latest jobs, the skills.
				recipients lists the people to write to, with why: write to each, at most three, the job's contact first.
				With nobody listed, find one with two web searches at most: the company's recruiter or the role's hiring \
				manager, e.g. site:linkedin.com/in "Acme" ("Technical Recruiter" OR "Talent Acquisition"); judge by the \
				results' titles and snippets, write to the likeliest, and add them with add_recruiters. With no one \
				found, write to the hiring team, with no profile link.
				For each, call save_outreach_draft with job_id %1$d, their name, role, profile link and email when known:
				- linkedin_note: a connection note, at most %2$d characters with the spaces: their first name, who you are \
				in a few words, the role by its title, one fact from the CV that fits it, and a light ask (a short chat, \
				or sending the CV). No flattery, no links. One too long is refused: shorten it and save it again;
				- email_subject and email_body: 4-6 sentences: why you write, the role, two facts from the CV that match \
				the posting, the ask, and the sign-off with the person's name from the CV.
				Never claim what the CV doesn't support. Never send anything: the profile sends them itself.
				In the summary: whom you wrote to, and why them.
				""".formatted(jobId, OutreachDraft.LINKEDIN_NOTE_MAX_LENGTH);
	}

	/**
	 * A brief read before the call with the recruiter: where the offer stands, with JobWarden's market figures and
	 * the profile's other offers, what to ask for and how. Never a figure or an offer made up.
	 */
	private static String negotiateOffer(@Nullable Long jobId) {
		return """
				job is the posting, with its location and salary; cv gives the person's level: the headline, the jobs and \
				their years, the skills.
				Call list_offers: the profile's offers, their terms and yearly salaries in one currency; the others are \
				leverage.
				Call get_market_salaries with the role as the survey names it (it returns the roles it knows when it \
				doesn't know yours), the job's main programming language as skill, the countries of the job and of the \
				other offers, in the offer's currency. survey_imported false: say in the brief that the Stack Overflow \
				figures aren't in JobWarden yet (the salaries page imports them), and lean on the other sources.
				When the figures are thin, search the web for what the company pays the role (Levels.fyi, Glassdoor, its \
				salary bands), and read two or three results with a short question.
				Write the brief for someone about to call the recruiter, short, with the figures:
				- position: the offer against the market in its country (under the median, in the middle half, over the \
				upper quartile, by how much) and against the other offers;
				- target: the figure to ask for, in the offer's currency and period, and why it's fair; no more than the \
				evidence carries;
				- walk_away: the least worth accepting, and why;
				- leverage: the figures and facts that make the ask fair, each with its source;
				- asks: what else to ask for, should the salary not move;
				- script: a few lines for the call, warm and firm: thanks, the enthusiasm, the ask with its reason;
				- email: an email asking for it, from the greeting to the sign-off with the person's name;
				- watch_out: e.g. an answer due soon (respond_by), a salary over the market already;
				- sources: the sources with their years, and the addresses read.
				Never invent an offer or a figure: only list_offers', get_market_salaries' and what the pages you read \
				say. Save it with save_negotiation_brief and job_id %1$d.
				""".formatted(jobId);
	}

	/** Stories the profile tells again and again, drafted from the CV and finished by the profile. */
	private static final String WRITE_STORIES_STEPS = """
			cv is the full CV; story_titles the stories the bank has: don't write those again.
			Draft up to %d stories the STAR way, the strongest first: those with results and numbers, and those that show \
			what interviews ask about: a hard technical problem, leading or mentoring, a disagreement, a failure and its \
			lesson, a deadline, an improvement the person pushed for.
			Each: a title of a few words; situation (the team, the product, what was wrong), task (what the person had to \
			do), action (what they did themselves, step by step), result (with the CV's numbers); and skills, what it \
			shows.
			Only what the CV says: where it leaves a part out, put the question in brackets, e.g. [How many users did it \
			reach?], never a number made up. The person finishes them on the CV page.
			Save them with save_stories.
			In the summary: the stories you drafted, and what the person should fill in.
			""".formatted(StoryTools.DRAFT_MAX);

	/** How many jobs one request scores: the CV is read once for them all. */
	static final int SCORE_AT_ONCE = 15;

	/**
	 * The assistant reads the requirements; JobWarden works the score out from them, so scores compare across jobs and
	 * with how the applications went.
	 */
	private static final String SCORING_STEPS = """
			For a job without a description, read its link, asking for the requirements alone; for one cut short, read \
			the rest where description_with says.
			List %d to %d of the posting's requirements, short, in its words: the skills, tools, years, languages and \
			duties that matter, and the place, the level and the work type when it sets them. MUST when required \
			(required, must, you have, you are), NICE when a plus (nice to have, bonus, preferred, ideally).
			Judge each against the CV honestly: MET when the CV shows it, PARTIAL when it has something near it (a related \
			tool, fewer years, a smaller scale), MISSING when nothing supports it; a few words on why. Never count what \
			the CV doesn't say.
			Save it with save_job_fit and a summary of a sentence or two: JobWarden works out the score, from 1 to 5.
			""".formatted(FitTools.REQUIREMENTS_MIN, FitTools.REQUIREMENTS_MAX);

	/**
	 * Applying in the profile's own browser, with its sign-ins: never around a CAPTCHA, never on a site whose terms
	 * forbid it, and never with an answer made up. Claude Code drives Chrome with Claude in Chrome, whose tools the
	 * steps name; another assistant has the same steps with its own browser tools.
	 */
	private static final String AUTO_APPLY_STEPS = """
			You need a browser you drive in the profile's own Chrome, with its sign-ins (in Claude Code: Claude in Chrome). \
			Without one, finish the request with done false and say so.
			Call get_application_answers for the contact details and answers to use, and list_apply_queue for the jobs: \
			do them in order, each with start_apply first.
			For a job without a tailored CV (has_tailored_cv false), tailor one first: get_cv, get_job, then \
			save_tailored_cv, truthfully, in the posting's words. Download the job's CV from its cv_url, to upload.
			Open the job's link and its application form. Report SKIPPED, with why, for LinkedIn Easy Apply, a site whose \
			terms forbid applying automatically, a form behind a sign-in the profile doesn't have, or a closed posting.
			Spare the context in the browser: find the form's fields by reading the page's interactive elements (find, \
			read_page), fill them in as form inputs (form_input), and take a screenshot only to see a step that went wrong.
			Fill the form in: the contact details and the CV from JobWarden; every question from the answers, as they \
			are; a cover letter only when cover_letter is WRITE or the form needs one: the job's own when \
			has_cover_letter is true (get_cover_letter for its text, cover_letter_url for its PDF), else write a short \
			one for this job from the CV, and keep it with save_cover_letter before using it, so the profile sees what \
			was sent; the voluntary questions (gender, ethnicity, disability, veteran) declined when decline_voluntary is \
			true. Write answers to open questions, e.g. why this company, from the CV and the posting. Never make up a \
			fact: a question about the person that neither the answers nor the CV answers, e.g. a number, a date, a yes \
			or no, stops the job: leave the form, and report NEEDS_ANSWER with the question word for word.
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
				job is the posting (without a description, read its link; cut short, the rest where description_with \
				says); cv is the full CV: the fields, and the text of the file uploaded.
				List what the posting asks for: skills, tools, certifications and duties, must-haves first, in its own \
				words, e.g. Spring Boot rather than Spring, and Amazon Web Services (AWS) spelled out once.
				Rewrite the CV for this job, truthfully: never add a skill, an employer, a title, a date, a degree or a \
				number the CV doesn't support. Reword, reorder and choose instead:
				- headline: the posting's title, when it's true of the person;
				- summary: three or four lines on why the person fits, with the most important keywords;
				- experience: the same jobs and dates, newest first; for each, three to six points, a line each, starting \
				with a verb, naming the tools, with the CV's results and numbers; what matters for this job first, and \
				what doesn't left out;
				- skills: the posting's keywords the person has first, in its words, then the other relevant ones, ten to \
				twenty in all;
				- education, languages and certifications as they are.
				Plain for the screening software: no tables, columns, icons or graphics; JobWarden lays it out in one \
				column. Leave out the contact details: JobWarden keeps the CV page's.
				Save it with save_tailored_cv and job_id %1$d, with matched_keywords (the posting's keywords the CV now \
				names), missing_keywords (those the person can't honestly claim) and notes: what you changed and what to \
				check before sending it.
				""".formatted(jobId);
	}

	/**
	 * A brief read the evening before an interview: who the company is, what they'll ask, what to tell them, what to
	 * ask them. Found cheaply, from a few pages read with a question each.
	 */
	private static String prepareInterview(@Nullable Long jobId) {
		return """
				job is the posting with its timeline, cv the full CV, stories the person's story bank, told the STAR way.
				Find out about the company: search the web for its name with about, news, engineering blog and interview \
				questions (Glassdoor, Reddit, Blind). Read the three to six most useful results with a short question \
				each, not whole.
				Write the brief for someone reading it the evening before, short and to the point:
				- company: what they make and for whom, how big, where, how they're doing, and what's new this year;
				- role: what the job is really about, and what they'll look for, from the posting;
				- questions: eight to twelve questions likely asked, about the posting's tools and about the person, \
				those candidates report first;
				- stories: for what they'll look for, a story each, in a line or two, with the result: from stories \
				first, then from the CV; never one neither supports;
				- story_ids: the ids of the stories that answer what they'll look for, the best first;
				- ask_them: four to six good questions to ask them;
				- watch_out: what to look into before saying yes, e.g. layoffs, reviews, a vague salary; left out when \
				there's nothing;
				- sources: the addresses you read.
				Save it with save_interview_brief and job_id %1$d.
				""".formatted(jobId);
	}

}
