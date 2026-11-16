package com.jobwarden.web.view;

/**
 * Which jobs the Cover letters page lists.
 */
public enum LetterFilter {

	ALL, WITHOUT, WITH;

	public boolean matches(LetterRow row) {
		return switch (this) {
			case ALL -> true;
			case WITHOUT -> !row.hasLetter();
			case WITH -> row.hasLetter();
		};
	}

}
