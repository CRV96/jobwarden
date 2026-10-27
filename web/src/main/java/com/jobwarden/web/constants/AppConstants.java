package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppConstants {

	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class ControllerConstants {
		public static final String HOMEPAGE = "/";
		public static final String APPLICATIONS = "/applications";
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
		public static final String NEW_APPLICATION = "applications/new";
		public static final String DISCOVER = "discover/discover";
		/** The card to review and what changes with it, which HTMX swaps after a decision. */
		public static final String DISCOVER_REVIEW_FRAGMENT = DISCOVER + " :: review";
		/** What the search page shows once a search is saved. */
		public static final String SEARCH_SAVED_FRAGMENT = "search/search :: search-saved";
		public static final String PIPELINE = "pipeline/board";
		public static final String PROFILE_LIST = "profiles/list";
		public static final String SALARIES = "salaries/salaries";
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
		/** How many applications the selected profile has, next to Jobs in the sidebar. */
		public static final String APPLICATION_COUNT = "applicationCount";
		/** How many found jobs wait on the Discover page, next to Discover in the sidebar. */
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
		/** The pipeline's columns: each status with its applications, in the order of the statuses. */
		public static final String PIPELINE_COLUMNS = "pipelineColumns";
		public static final String APPLICATIONS = "applications";
		public static final String JOB_APPLICATION = "jobApplication";
		public static final String STATUSES = "statuses";
		public static final String SELECTED_STATUS = "selectedStatus";
		public static final String REJECTION_REASONS = "rejectionReasons";
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
		/** The saved search the Discover page shows the jobs of, or {@code null} for all of them. */
		public static final String SELECTED_SEARCH = "selectedSearch";
		/** The latest decisions on the Discover page, each a {@code FoundJob}. */
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
