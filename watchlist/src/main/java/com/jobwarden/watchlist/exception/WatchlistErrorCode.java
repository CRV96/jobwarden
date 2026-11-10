package com.jobwarden.watchlist.exception;

import com.jobwarden.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WatchlistErrorCode implements ErrorCode {

	WATCHED_COMPANY_NOT_FOUND("WATCHLIST-001"),

	/** The link isn't on Greenhouse, Lever or Ashby, or doesn't name a board. */
	NOT_A_BOARD_LINK("WATCHLIST-002"),

	/** The board's system answered that it has no such board. */
	BOARD_NOT_FOUND("WATCHLIST-003"),

	/** The board couldn't be read: the system didn't answer, or answered with an error. */
	BOARD_NOT_READ("WATCHLIST-004");

	private final String code;

}
