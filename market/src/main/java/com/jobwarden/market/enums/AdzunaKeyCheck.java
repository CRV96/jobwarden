package com.jobwarden.market.enums;

/**
 * What Adzuna's API said to a key before it was saved.
 */
public enum AdzunaKeyCheck {

	/** It answered with the key: saved */
	ACCEPTED,

	/** It turned the key down: not saved */
	REFUSED,

	/** It couldn't be reached, or answered with another error: saved all the same */
	UNCHECKED

}
