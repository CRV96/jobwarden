package com.jobwarden.mcp.enums;

/**
 * Whether a posting is still up, as far as fetching its page tells.
 */
public enum Verdict {

	OPEN,

	CLOSED,

	/** The page couldn't tell: the site blocks automated visits, or it didn't answer. */
	UNKNOWN

}
