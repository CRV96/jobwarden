package com.jobwarden.discover.enums;

/**
 * What the profile can ask Claude to do: the AI Assistant page's presets, a CV for one job and a brief for its
 * interview from the job's page, hiring posts and recruiters from the Who's hiring page, applying to jobs from the
 * Auto-apply page, sorting the emails on the Inbox page, and the CV's projects from GitHub on the CV page.
 */
public enum AiRequestKind {

	/** Search the web for open jobs that fit the profile's CV, and the requirements given. */
	FIND_JOBS_FOR_CV,

	/** Search the web for open jobs that fit the requirements given, and the CV when it says so. */
	FIND_JOBS,

	/** Run the saved searches' queries, and add the new postings. */
	RUN_SAVED_SEARCHES,

	/** Check whether the postings of the jobs still waiting are up, and close those that certainly aren't. */
	CHECK_POSTINGS,

	/** Move the applications with no news for a month to No response, and set a day to follow up on the newer ones. */
	FOLLOW_UP,

	/**
	 * Rewrite the profile's CV for one job: with the posting's own words for what the person really did, in a form
	 * the screening software employers run CVs through reads. The request names the job.
	 */
	TAILOR_CV,

	/** Search the web for recruiters' posts saying they're hiring, for the Who's hiring page. */
	FIND_HIRING_POSTS,

	/** Search the web for recruiters who hire for the requirements, and at the companies of the profile's jobs. */
	FIND_RECRUITERS,

	/** Apply to the jobs queued on the Auto-apply page, in the profile's browser. */
	AUTO_APPLY,

	/**
	 * Prepare the profile for an interview for one job: a brief about the company and the role, the questions likely
	 * asked, the profile's own stories to tell, and questions to ask them. The request names the job.
	 */
	PREPARE_INTERVIEW,

	/** Score how well the profile fits one job, from the posting's requirements against the CV. The request names the job. */
	SCORE_JOB,

	/** Score the jobs still to apply to, or waiting on an answer, that have no score yet. */
	SCORE_JOBS,

	/**
	 * Sort the updates waiting on the Inbox page, found in the profile's emails: find the job each is about, apply it
	 * to that job's timeline, or dismiss it when it isn't news. What Claude isn't sure of stays for the profile.
	 */
	SORT_INBOX,

	/** Write the CV's personal projects from the GitHub repositories the profile chose on the CV page. */
	WRITE_PROJECTS;

	/**
	 * @return whether it says what to look for: titles, locations, skills...
	 */
	public boolean hasRequirements() {
		return findsJobs() || this == FIND_HIRING_POSTS || this == FIND_RECRUITERS;
	}

	/**
	 * @return whether it's about one job, which the request then names
	 */
	public boolean isAboutAJob() {
		return this == TAILOR_CV || this == PREPARE_INTERVIEW || this == SCORE_JOB;
	}

	/**
	 * @return whether Claude finds jobs for it, which then wait on the AI Assistant page
	 */
	public boolean findsJobs() {
		return this == FIND_JOBS_FOR_CV || this == FIND_JOBS || this == RUN_SAVED_SEARCHES;
	}

}
