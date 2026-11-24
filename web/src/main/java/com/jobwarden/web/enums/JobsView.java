package com.jobwarden.web.enums;

import com.jobwarden.web.constants.ControllerConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * How the Jobs page shows the jobs: as a list, a table to search, filter, sort and page, or as a board, a column per
 * status. The page remembers the last one in a cookie, which the sidebar's Jobs link opens again.
 */
@Getter
@RequiredArgsConstructor
public enum JobsView {

	LIST(ControllerConstants.APPLICATIONS),

	BOARD(ControllerConstants.JOB_BOARD);

	/** The cookie that keeps the last view, a year: only the server reads it. */
	public static final String COOKIE = "jobwarden-jobs-view";

	/** The page's address in this view. */
	private final String path;

	/**
	 * @param cookie the cookie's value, or {@code null} without one
	 * @return the view it names; the list for anything else
	 */
	public static JobsView of(@Nullable String cookie) {
		return BOARD.name().equals(cookie) ? BOARD : LIST;
	}

}
