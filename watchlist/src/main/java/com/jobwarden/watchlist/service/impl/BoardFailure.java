package com.jobwarden.watchlist.service.impl;

/**
 * A board couldn't be read: its system has no such board, didn't answer, or answered with something else than a
 * board.
 */
class BoardFailure extends RuntimeException {

	private final boolean notFound;

	BoardFailure(String message, boolean notFound) {
		super(message);
		this.notFound = notFound;
	}

	BoardFailure(String message) {
		this(message, false);
	}

	/**
	 * @return whether the system answered that it has no such board
	 */
	boolean notFound() {
		return this.notFound;
	}

}
