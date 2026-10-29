package com.jobwarden.hiring.enums;

/**
 * How a recruiter came to the profile. The first way stays: a recruiter Claude found who then posts is still found.
 */
public enum RecruiterOrigin {

	/** The author of a hiring post */
	POST,

	/** Found by Claude, asked for recruiters */
	FOUND,

	/** The contact saved on one of the profile's jobs */
	CONTACT,

	/** Added by hand on the Who's hiring page */
	HAND

}
