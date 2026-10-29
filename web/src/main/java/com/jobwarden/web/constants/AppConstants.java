package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppConstants {

	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class ControllerConstants {
		public static final String HOMEPAGE = "/";
		public static final String APPLICATIONS = "/applications";
		public static final String CALENDAR = "/calendar";
		public static final String HIRING = "/hiring";
		public static final String APPLY = "/apply";
		public static final String CV = "/cv";
		public static final String DISCOVER = "/discover";
		public static final String PIPELINE = "/pipeline";
		public static final String PROFILES = "/profiles";
		public static final String SALARIES = "/salaries";
		public static final String SEARCH = "/search";
		public static final String SETTINGS = "/settings";
		/** The icons of the sites postings came from: /sources/{source}/icon. */
		public static final String SOURCES = "/sources";
	}

	/**
	 * Headers HTMX sends and understands. See https://htmx.org/reference/#headers
	 */
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class HtmxHeaders {
		/** Sent by HTMX on every request it makes. */
		public static final String REQUEST = "HX-Request";
		/** Tells HTMX to load another page, instead of swapping the response into the current one. */
		public static final String REDIRECT = "HX-Redirect";
		/** Tells HTMX to reload the current page, e.g. the list, with its search and filters. */
		public static final String REFRESH = "HX-Refresh";
		/** Tells HTMX to fire these events on the page, for the parts of it that listen. */
		public static final String TRIGGER = "HX-Trigger";
	}

	/**
	 * Events the server fires on the page with {@link HtmxHeaders#TRIGGER}. The templates listen with
	 * {@code hx-trigger="<event> from:body"}.
	 */
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class HtmxEvents {
		/** An application changed in the side panel, so the list or the pipeline behind it reloads. */
		public static final String APPLICATIONS_CHANGED = "applications-changed";
		/** Claude added jobs to review, so the AI Assistant page's card loads again. */
		public static final String FOUND_JOBS_CHANGED = "found-jobs-changed";
	}

	/**
	 * Thymeleaf templates, by their path under {@code templates/}.
	 */
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class Views {
		public static final String APPLICATION_LIST = "applications/list";
		public static final String APPLICATION_DETAIL = "applications/detail";
		/** The part of the detail page that HTMX swaps after an event is added. */
		public static final String APPLICATION_DETAIL_FRAGMENT = APPLICATION_DETAIL + " :: application";
		/** The same application in the side panel the list and the pipeline open over themselves. */
		public static final String APPLICATION_DRAWER_FRAGMENT = "applications/drawer :: drawer";
		/** What the notes field shows once its text is saved. */
		public static final String NOTES_SAVED_FRAGMENT = "applications/fragments :: notes-saved";
		/** What the contact and follow-up card shows once its fields are saved, or why they aren't. */
		public static final String FOLLOW_UP_STATUS_FRAGMENT = "applications/fragments :: follow-up-status";
		public static final String NEW_APPLICATION = "applications/new";
		/** A job's AI-proof CV, which HTMX loads after the job and swaps as it changes. */
		public static final String TAILORED_CV_FRAGMENT = "applications/tailored-cv :: card";
		public static final String CALENDAR = "calendar/calendar";
		public static final String HIRING = "hiring/hiring";
		public static final String APPLY = "apply/apply";
		/** The Auto-apply page's run of Claude, which HTMX asks for again while it's open. */
		public static final String APPLY_RUN_FRAGMENT = APPLY + " :: run";
		/** One post on the Who's hiring page, which HTMX swaps once it's saved as a job. */
		public static final String HIRING_POST_FRAGMENT = HIRING + " :: post";
		/** What the Who's hiring page asked Claude, which HTMX swaps as it's asked, and while Claude is on it. */
		public static final String HIRING_ASK_FRAGMENT = HIRING + " :: ask";
		/** A recruiter's follow button, which HTMX swaps wherever it shows, once followed or not. */
		public static final String HIRING_FOLLOW_FRAGMENT = HIRING + " :: follow-button";
		public static final String CV = "cv/cv";
		/** The CV built from the CV page's fields: its preview, and the HTML its PDF is made from. */
		public static final String CV_DOCUMENT = "cv/document";
		public static final String DISCOVER = "discover/discover";
		/** The card to review and what changes with it, which HTMX swaps after a decision. */
		public static final String DISCOVER_REVIEW_FRAGMENT = DISCOVER + " :: review";
		/** The requests to Claude, which HTMX swaps as one is made, cancelled, or changes while it's open. */
		public static final String DISCOVER_REQUESTS_FRAGMENT = DISCOVER + " :: requests";
		/** What the search page shows once a search is saved. */
		public static final String SEARCH_SAVED_FRAGMENT = "search/search :: search-saved";
		public static final String PIPELINE = "pipeline/board";
		public static final String PROFILE_LIST = "profiles/list";
		public static final String SALARIES = "salaries/salaries";
		/** The salaries page's market section, which HTMX loads after the page and swaps as its choices change. */
		public static final String SALARIES_MARKET_FRAGMENT = "salaries/market :: market";
		public static final String SEARCH = "search/search";
		/** The search page's queries, which HTMX swaps as the filters change. */
		public static final String SEARCH_OUTPUT_FRAGMENT = SEARCH + " :: output";
		public static final String SETTINGS = "settings/settings";
	}

	/**
	 * Names the templates read from the model. Thymeleaf reserves {@code application}, {@code session} and
	 * {@code param}, so never use those.
	 */
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class ModelAttributes {
		/** The selected profile, or {@code null}; every page's sidebar shows it. */
		public static final String CURRENT_PROFILE = "currentProfile";
		/** Every profile, for switching to another one from the sidebar. */
		public static final String SIDEBAR_PROFILES = "sidebarProfiles";
		/** The version running and the latest release, an {@code UpdateStatus}: the sidebar and the settings show it. */
		public static final String UPDATE = "update";
		/** How many applications the selected profile has, next to Jobs in the sidebar. */
		public static final String APPLICATION_COUNT = "applicationCount";
		/** How many found jobs wait on the AI Assistant page, next to AI Assistant in the sidebar. */
		public static final String DISCOVER_COUNT = "discoverCount";
		/** The languages the settings page offers. */
		public static final String LANGUAGES = "languages";
		/** The selected language, as a {@code Language}. */
		public static final String CURRENT_LANGUAGE = "currentLanguage";
		/** The address of the app, for the browser extension's settings. */
		public static final String SERVER_URL = "serverUrl";
		/** What the list shows: its search, status filter, order and page, as an {@code ApplicationListParams}. */
		public static final String LIST_PARAMS = "listParams";
		/** The list's rows, a {@code Page} of them. */
		public static final String APPLICATION_PAGE = "applicationPage";
		/** How many of the applications the search finds have each status, for the status filter. */
		public static final String STATUS_COUNTS = "statusCounts";
		/** The sites the profile has an icon for (a set of sources): the pages show it instead of a generic one. */
		public static final String ICON_SOURCES = "iconSources";
		/** The profile's applications with a company logo (a set of ids): the pages show it before the job's title. */
		public static final String LOGO_APPLICATIONS = "logoApplications";
		/**
		 * The calendar page's view (a {@code CalendarView}), the views it offers, and the day it shows the month,
		 * week or day of (a {@code LocalDate}).
		 */
		public static final String CALENDAR_VIEW = "calendarView";
		public static final String CALENDAR_VIEWS = "calendarViews";
		public static final String CALENDAR_DATE = "calendarDate";
		/** The month view's grid, a {@code CalendarMonth}; the week and day views' days, a {@code CalendarDays}. */
		public static final String CALENDAR_MONTH = "calendarMonth";
		public static final String CALENDAR_DAYS = "calendarDays";
		/** What's coming up, next to the calendar: a list of {@code CalendarEntry}. */
		public static final String UPCOMING = "upcoming";
		/** The profile's CV, a {@code Cv}, and the fields the CV page shows, a {@code CvDetails}. */
		public static final String CV = "cv";
		public static final String CV_DETAILS = "cvDetails";
		/** Why the uploaded CV file wasn't kept, or {@code null}. */
		public static final String UPLOAD_ERROR = "uploadError";
		/** The largest CV file, in bytes, which the page checks before sending one. */
		public static final String CV_MAX_BYTES = "cvMaxBytes";
		/** What importing a CSV file of jobs did, a {@code CsvImport}, or why it couldn't: shown once, in the settings. */
		public static final String CSV_IMPORT = "csvImport";
		public static final String CSV_IMPORT_ERROR = "csvImportError";
		/** The key in use for Adzuna's API, an {@code AdzunaKey}: in the settings. */
		public static final String ADZUNA_KEY = "adzunaKey";
		/**
		 * After saving an Adzuna key, shown once: what Adzuna said (an {@code AdzunaKeyCheck}), or why the form was
		 * refused; and the app ID again, after either, but never the app key. After removing the saved key, that it was.
		 */
		public static final String ADZUNA_CHECK = "adzunaCheck";
		public static final String ADZUNA_ERROR = "adzunaError";
		public static final String ADZUNA_APP_ID = "adzunaAppId";
		public static final String ADZUNA_REMOVED = "adzunaRemoved";
		/** Today's date, for the pages that mark it. */
		public static final String TODAY = "today";
		/** The pipeline's columns: each status with its applications, in the order of the statuses. */
		public static final String PIPELINE_COLUMNS = "pipelineColumns";
		public static final String APPLICATIONS = "applications";
		public static final String JOB_APPLICATION = "jobApplication";
		public static final String STATUSES = "statuses";
		public static final String SELECTED_STATUS = "selectedStatus";
		public static final String REJECTION_REASONS = "rejectionReasons";
		public static final String INTERVIEW_FORMATS = "interviewFormats";
		/** The current date and time, to the minute: the default of the forms' date and time fields. */
		public static final String NOW = "now";
		/** Where an application's parts render: its own page, or the side panel. */
		public static final String VIEW = "view";
		/** What was typed into a form that's shown again with errors. */
		public static final String FORM = "form";
		/** Each form field's error message, by field name. */
		public static final String FIELD_ERRORS = "fieldErrors";
		public static final String EVENT_ERROR = "eventError";
		public static final String PROFILES = "profiles";
		public static final String NAME = "name";
		public static final String NAME_ERROR = "nameError";
		/** What the profile's jobs pay, a {@code SalaryOverview}. */
		public static final String SALARY_OVERVIEW = "overview";
		/** The periods the salaries page can show salaries per. */
		public static final String SALARY_PERIODS = "salaryPeriods";
		/** The axes of the salaries page's charts, each a {@code ChartScale}: the ranges, and the medians by status. */
		public static final String RANGE_SCALE = "rangeScale";
		public static final String STATUS_SCALE = "statusScale";
		/** What the market pays across countries, a {@code MarketSalaries}, and its chart's axis, a {@code ChartScale}. */
		public static final String MARKET = "market";
		public static final String MARKET_SCALE = "marketScale";
		/** The market section's choices, a {@code MarketForm}, and the address that loads the section with them. */
		public static final String MARKET_FORM = "marketForm";
		public static final String MARKET_URL = "marketUrl";
		/** The roles and skills the market section offers, lists of text. */
		public static final String MARKET_ROLES = "marketRoles";
		public static final String MARKET_SKILLS = "marketSkills";
		/** The found job to review now, a {@code FoundJob}, or {@code null} when none waits. */
		public static final String FOUND_JOB = "foundJob";
		/** How many found jobs wait for a decision, for the search shown, and for all of them. */
		public static final String TO_REVIEW = "toReview";
		public static final String TO_REVIEW_ALL = "toReviewAll";
		/** The profile's saved searches, each a {@code SavedSearch}. */
		public static final String SAVED_SEARCHES = "savedSearches";
		/** The search page's address for each saved search (search id → address), to run or change it there. */
		public static final String SEARCH_URLS = "searchUrls";
		/** How many found jobs wait for each saved search (search id → count), and for none (key 0). */
		public static final String SEARCH_COUNTS = "searchCounts";
		/** The saved search the AI Assistant page shows the jobs of, or {@code null} for all of them. */
		public static final String SELECTED_SEARCH = "selectedSearch";
		/** The latest requests to Claude, each an {@code AiRequest}; and whether one is still open. */
		public static final String AI_REQUESTS = "aiRequests";
		public static final String AI_OPEN = "aiOpen";
		/** Why a request to Claude wasn't made, or {@code null}. */
		public static final String AI_ERROR = "aiError";
		/** The jobs found for the latest requests (request id → list of {@code FoundJob}). */
		public static final String AI_REQUEST_JOBS = "aiRequestJobs";
		/** The prompt that hands each waiting request to Claude Code (request id → {@code ClaudePrompts.Prompt}). */
		public static final String AI_PROMPTS = "aiPrompts";
		/** The prompt that has Claude Code pick up every request on its own, now and then. */
		public static final String AI_LOOP_PROMPT = "aiLoopPrompt";
		/** Whether the profile has a CV, uploaded or filled in, for the preset that matches jobs against it. */
		public static final String HAS_CV = "hasCv";
		/**
		 * A job's AI-proof CV: the job's id, its {@code TailoredCv} or {@code null}, the latest request to tailor it or
		 * {@code null}, and the prompt that hands that request to Claude Code while it waits.
		 */
		public static final String CV_JOB_ID = "cvJobId";
		public static final String TAILORED_CV = "tailoredCv";
		public static final String CV_REQUEST = "cvRequest";
		public static final String CV_PROMPT = "cvPrompt";
		/**
		 * The Who's hiring page: what it shows ({@code HiringParams}), its posts and recruiters, the hashtags and
		 * countries of all the posts to filter by, the latest request to Claude for posts with its prompt, and why a
		 * recruiter wasn't added. One post, or one recruiter, for the parts HTMX swaps.
		 */
		public static final String HIRING_PARAMS = "hiringParams";
		public static final String HIRING_POSTS = "hiringPosts";
		public static final String RECRUITERS = "recruiters";
		public static final String HIRING_TAGS = "hiringTags";
		public static final String HIRING_COUNTRIES = "hiringCountries";
		public static final String HIRING_REQUEST = "hiringRequest";
		public static final String HIRING_PROMPT = "hiringPrompt";
		public static final String HIRING_ERROR = "hiringError";
		public static final String POST = "post";
		public static final String RECRUITER = "recruiter";
		/** The tabs and the "posted within" choices the Who's hiring page offers. */
		public static final String HIRING_TABS = "hiringTabs";
		public static final String POSTED_PERIODS = "postedPeriods";
		/**
		 * The Recruiters tab: the recruiters its filter and its search show, how many each filter has (a map), and
		 * the filters.
		 */
		public static final String RECRUITERS_SHOWN = "recruitersShown";
		public static final String RECRUITER_COUNTS = "recruiterCounts";
		public static final String RECRUITER_FILTERS = "recruiterFilters";
		/** What the page asks Claude for, an {@code AiRequestKind}, and how many recruiters it can ask for. */
		public static final String ASK_KIND = "askKind";
		public static final String RECRUITER_COUNTS_OFFERED = "recruiterCountsOffered";
		/** Whether the follow button answers HTMX, to take the place of each of the recruiter's (hx-swap-oob). */
		public static final String FOLLOW_OOB = "followOob";
		/**
		 * The Auto-apply page: the answers its form shows (an {@code ApplicationAnswers}, or the form typed when it
		 * didn't save), the Saved jobs Claude can apply to, the jobs queued with how each went, and the latest run of
		 * Claude with its prompt. The CV's contact details show with the answers.
		 */
		public static final String APPLY_ANSWERS = "applyAnswers";
		public static final String APPLY_JOBS = "applyJobs";
		public static final String APPLY_ITEMS = "applyItems";
		public static final String APPLY_RUN = "applyRun";
		public static final String APPLY_PROMPT = "applyPrompt";
		public static final String APPLY_ERROR = "applyError";
		/** The cover letter choices of the Auto-apply page's answers. */
		public static final String COVER_LETTERS = "coverLetters";
		/** The CV page's list of CVs tailored to jobs, each a {@code TailoredCv}, and their jobs (id → summary). */
		public static final String TAILORED_CVS = "tailoredCvs";
		public static final String TAILORED_JOBS = "tailoredJobs";
		/** The latest decisions on the AI Assistant page, each a {@code FoundJob}. */
		public static final String RECENT_DECISIONS = "recentDecisions";
		/** A search just saved, a {@code SavedSearch}. */
		public static final String SAVED_SEARCH = "savedSearch";
		/** The address Claude connects to: the app's MCP endpoint. */
		public static final String MCP_URL = "mcpUrl";
		/** What the search page searches for, a {@code SearchCriteria}. */
		public static final String SEARCH_CRITERIA = "criteria";
		/** The search page's queries, a {@code GeneratedQueries}, and the platform whose query it shows. */
		public static final String QUERIES = "queries";
		public static final String SELECTED_PLATFORM = "selectedPlatform";
		public static final String PLATFORMS = "platforms";
		/** Each platform's icon, by the platform's name. */
		public static final String PLATFORM_ICONS = "platformIcons";
		public static final String GOOGLE_WORD_LIMIT = "googleWordLimit";
		/** The description the search page's filters were filled in from, if they were. */
		public static final String DESCRIPTION = "description";
		/** The descriptions the search page offers as examples. */
		public static final String EXAMPLES = "examples";
		public static final String WORK_TYPES = "workTypes";
		public static final String EMPLOYMENT_TYPES = "employmentTypes";
		public static final String RECENCIES = "recencies";
		/** What each field of chips on the search page suggests, lists of {@code TagSuggestion}. */
		public static final String TITLE_SUGGESTIONS = "titleSuggestions";
		public static final String LOCATION_SUGGESTIONS = "locationSuggestions";
		public static final String SKILL_SUGGESTIONS = "skillSuggestions";
		public static final String SITE_SUGGESTIONS = "siteSuggestions";
		/** The job boards the search page offers to add with one click. */
		public static final String JOB_BOARDS = "jobBoards";
	}
}
