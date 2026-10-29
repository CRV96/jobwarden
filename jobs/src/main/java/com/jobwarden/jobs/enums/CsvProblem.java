package com.jobwarden.jobs.enums;

/**
 * Why a row of a CSV file wasn't imported.
 */
public enum CsvProblem {

	/** The row has no title. */
	NO_TITLE,

	/** The status is none of the statuses, nor a usual name for one, e.g. Wishlist or Interview. */
	UNKNOWN_STATUS,

	/** The rejection reason is none of the reasons. */
	UNKNOWN_REJECTION_REASON,

	/** A date isn't a date, e.g. 2026-10-28 or 28.10.2026. */
	BAD_DATE,

	/** The link isn't a web address. */
	BAD_LINK,

	/** The file has more rows than an import takes: this one and those after it weren't read. */
	TOO_MANY_ROWS

}
