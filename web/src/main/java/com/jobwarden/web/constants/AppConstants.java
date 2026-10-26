package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppConstants {

	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class ControllerConstants {
		public static final String HOMEPAGE = "/";
		public static final String APPLICATIONS = "/applications";
		public static final String PIPELINE = "/pipeline";
		public static final String PROFILES = "/profiles";
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
		public static final String PIPELINE = "pipeline/board";
		public static final String PROFILE_LIST = "profiles/list";
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
	}
}
