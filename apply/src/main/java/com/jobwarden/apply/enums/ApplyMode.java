package com.jobwarden.apply.enums;

/**
 * How far Claude goes with a form.
 */
public enum ApplyMode {

	/** Fills the form in and leaves it for the profile to check and send */
	REVIEW,

	/** Sends the form once every answer it asks for is known */
	SUBMIT

}
