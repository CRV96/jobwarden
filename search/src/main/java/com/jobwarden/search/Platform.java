package com.jobwarden.search;

/**
 * Where a query is meant to run. Each gets a query of its own (see {@link QueryGenerator}).
 */
public enum Platform {

	/** Google, limited to the job boards companies host their openings on (applicant tracking systems). */
	GOOGLE,
	LINKEDIN,
	/** Google, limited to the subreddits where jobs are posted. */
	REDDIT,
	/** X's search of recent posts, for "we're hiring" posts. */
	X,
	/** Google, limited to the remote job boards. */
	REMOTE_BOARDS

}
