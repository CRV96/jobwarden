package com.jobwarden.jobs.enums;

/**
 * What JobWarden notices about a posting on its own, with no AI: things to know before applying.
 */
public enum FitFlagKind {

	/** The posting rules out sponsoring a visa, and the profile said it needs one: a dealbreaker. */
	NO_SPONSORSHIP,

	/** The profile has the same job, from the same company, under another link: posted again, maybe never filled. */
	REPOSTED,

	/** The posting went up long ago, and is still up: perhaps a ghost job, never really meant to be filled. */
	OPEN_LONG

}
