package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The paths of the pages and of what they ask for, as the controllers map them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControllerConstants {
	public static final String HOMEPAGE = "/";
	public static final String APPLICATIONS = "/applications";
	public static final String CALENDAR = "/calendar";
	public static final String HIRING = "/hiring";
	public static final String APPLY = "/apply";
	public static final String CV = "/cv";
	public static final String DISCOVER = "/discover";
	/** The companies whose job boards JobWarden reads itself, with no Claude tokens. */
	public static final String WATCHLIST = "/watchlist";
	/** The jobs as a board, a column per status: the other view of the Jobs page. */
	public static final String JOB_BOARD = APPLICATIONS + "/board";
	/** Where the board was before it was a view of the Jobs page: old bookmarks go on to it. */
	public static final String PIPELINE = "/pipeline";
	public static final String INBOX = "/inbox";
	/** Setting the reminders of a calendar entry, and those that are due, which the pages ask for. */
	public static final String REMINDERS = "/reminders";
	public static final String PROFILES = "/profiles";
	public static final String SALARIES = "/salaries";
	/** The profile's offers side by side, with what the market pays. */
	public static final String OFFERS = "/offers";
	/** Every job's cover letter and messages in one place, and the base letter they start from. */
	public static final String LETTERS = "/letters";

	public static final String STATISTICS = "/statistics";
	public static final String SEARCH = "/search";
	public static final String SETTINGS = "/settings";
	/** The setup guide: what to set up for each part of JobWarden to work, which the pages open in a dialog. */
	public static final String SETUP = "/setup";
	/** What each release of JobWarden changed, from GitHub; and, once after an update, the new ones in a popup. */
	public static final String UPDATES = "/updates";
	public static final String WHATS_NEW = UPDATES + "/whats-new";
	/** Run: JobWarden starts the profile's AI assistant itself, in the background, on requests waiting. */
	public static final String ASSISTANT_RUN = "/assistant/run";
	/** The icons of the sites postings came from: /sources/{source}/icon. */
	public static final String SOURCES = "/sources";
}
