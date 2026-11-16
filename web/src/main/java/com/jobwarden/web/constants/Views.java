package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Thymeleaf templates, by their path under {@code templates/}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Views {
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
	/** A job's fit, which HTMX loads after the job and swaps as it changes. */
	public static final String JOB_FIT_FRAGMENT = "applications/job-fit :: card";
	/** A job's interview brief, which HTMX loads after the job and swaps as it changes. */
	public static final String INTERVIEW_BRIEF_FRAGMENT = "applications/interview-brief :: card";
	/** A job's AI-proof CV, which HTMX loads after the job and swaps as it changes. */
	public static final String TAILORED_CV_FRAGMENT = "applications/tailored-cv :: card";
	/** A job's offer terms, on its page while it's at Offer, which HTMX loads after the job. */
	public static final String OFFER_TERMS_FRAGMENT = "applications/offer :: terms";
	/** What the offer terms card shows once its fields are saved, or why they aren't. */
	public static final String OFFER_TERMS_STATUS_FRAGMENT = "applications/offer :: terms-status";
	/** A job's negotiation brief, which HTMX loads after the job and swaps as it changes. */
	public static final String NEGOTIATION_FRAGMENT = "applications/offer :: negotiation";
	/** A job's cover letter, which HTMX loads after the job and swaps as it changes. */
	public static final String COVER_LETTER_FRAGMENT = "applications/cover-letter :: card";
	/** A cover letter as a page: its preview, and the HTML its PDF is made from. */
	public static final String COVER_LETTER_DOCUMENT = "applications/letter-document";
	/** A job's messages to the people who hire for it, which HTMX loads after the job and swaps as they change. */
	public static final String OUTREACH_FRAGMENT = "applications/outreach :: card";
	/** One message, which HTMX swaps once it's saved. */
	public static final String OUTREACH_DRAFT_FRAGMENT = "applications/outreach :: draft";
	/** The request for a job's messages while it's open, which HTMX asks for again until it isn't. */
	public static final String OUTREACH_WAITING_FRAGMENT = "applications/outreach :: waiting";
	/** That the request is done, with a button to show what Claude wrote: messages typed in meanwhile stay. */
	public static final String OUTREACH_DONE_FRAGMENT = "applications/outreach :: done";
	public static final String LETTERS = "letters/letters";
	/** The Cover letters page's base letter, which HTMX swaps as it's changed. */
	public static final String BASE_LETTER_FRAGMENT = LETTERS + " :: base";
	/** One job's letter and messages, beside the list, which HTMX loads as a job is picked. */
	public static final String LETTERS_PANEL_FRAGMENT = LETTERS + " :: panel";
	/** One job of the list, which HTMX asks for again while Claude writes its letter. */
	public static final String LETTERS_ROW_FRAGMENT = LETTERS + " :: row(oob=false)";
	/** The letters Claude has to write, with what to paste into Claude Code, asked for again while there are any. */
	public static final String LETTERS_WAITING_FRAGMENT = LETTERS + " :: waiting";
	/** What asking Claude for letters, or cancelling them, answers: the letters waiting, and the jobs' rows. */
	public static final String LETTERS_ASKED_FRAGMENT = LETTERS + " :: asked";
	public static final String CALENDAR = "calendar/calendar";
	/** The Subscribe menu's content, which HTMX swaps once there's a new address. */
	public static final String CALENDAR_FEED_FRAGMENT = CALENDAR + " :: feed";
	/** A job's reminders, in its side panel and on its page, which HTMX loads after the job. */
	public static final String REMINDERS_FRAGMENT = "calendar/reminders :: job-reminders";
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
	public static final String CV_FILE_PREVIEW = "cv/file-preview";
	public static final String GITHUB_FRAGMENT = "cv/github :: card";
	/** The CV page's story bank, which HTMX loads after the page and swaps after each change. */
	public static final String STORIES_FRAGMENT = "cv/stories :: card";
	public static final String DISCOVER = "discover/discover";
	/** The card to review and what changes with it, which HTMX swaps after a decision. */
	public static final String DISCOVER_REVIEW_FRAGMENT = DISCOVER + " :: review";
	/** What the AI Assistant has for a job, under its facts: the fit, and each card's state. */
	public static final String JOB_ASSISTANT_FRAGMENT = "applications/overview :: overview";
	/** What Run answers: that the assistant started, or why not, in place of the button. */
	public static final String ASSISTANT_RUN_FRAGMENT = "applications/claude :: run-result";
	/** The requests to Claude, which HTMX swaps as one is made, cancelled, or changes while it's open. */
	public static final String DISCOVER_REQUESTS_FRAGMENT = DISCOVER + " :: requests";
	public static final String WATCHLIST = "watchlist/watchlist";
	/** The watchlist's companies, the form to add some and the suggestions, which HTMX swaps after each change. */
	public static final String WATCHLIST_FRAGMENT = WATCHLIST + " :: watchlist";
	/** What saving a search answers: that it's saved, and the menu of saved filters, out of band. */
	public static final String SEARCH_SAVED_FRAGMENT = "search/search :: saved-answer";

	public static final String SAVED_FILTERS_FRAGMENT = "search/search :: saved-filters";
	public static final String PIPELINE = "pipeline/board";
	public static final String INBOX = "inbox/inbox";
	/** The emails waiting, which HTMX swaps once one is applied or dismissed. */
	public static final String EMAIL_UPDATES_FRAGMENT = INBOX + " :: updates";
	/** The mailbox card, which HTMX swaps when connecting fails, with why. */
	public static final String MAILBOX_FRAGMENT = INBOX + " :: mailbox";

	public static final String SORTING_FRAGMENT = INBOX + " :: sorting";
	/** A column's next cards, which HTMX adds at its end as it's scrolled. */
	public static final String PIPELINE_CARDS_FRAGMENT = PIPELINE + " :: cards";
	public static final String PROFILE_LIST = "profiles/list";
	/** A profile's password, asked when it's chosen */
	public static final String PROFILE_UNLOCK = "profiles/unlock";
	public static final String SALARIES = "salaries/salaries";
	public static final String OFFERS = "offers/offers";
	/** The offers page's row of market salaries, which HTMX loads after the page. */
	public static final String OFFERS_MARKET_FRAGMENT = "offers/market :: market";

	public static final String STATISTICS = "statistics/statistics";

	public static final String HOME = "home/home";
	/** The salaries page's market section, which HTMX loads after the page and swaps as its choices change. */
	public static final String SALARIES_MARKET_FRAGMENT = "salaries/market :: market";
	public static final String SEARCH = "search/search";
	/** The search page's queries, which HTMX swaps as the filters change. */
	public static final String SEARCH_OUTPUT_FRAGMENT = SEARCH + " :: queries";
	public static final String SETTINGS = "settings/settings";
}
