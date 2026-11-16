package com.jobwarden.web.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Headers HTMX sends and understands. See https://htmx.org/reference/#headers
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HtmxHeaders {
	/** Sent by HTMX on every request it makes. */
	public static final String REQUEST = "HX-Request";
	/** Tells HTMX to load another page, instead of swapping the response into the current one. */
	public static final String REDIRECT = "HX-Redirect";
	/** Tells HTMX to reload the current page, e.g. the list, with its search and filters. */
	public static final String REFRESH = "HX-Refresh";
	/** Tells HTMX to fire these events on the page, for the parts of it that listen. */
	public static final String TRIGGER = "HX-Trigger";
	/** Tells HTMX to swap the response into this element instead of the one the request named. */
	public static final String RETARGET = "HX-Retarget";
	/** Tells HTMX how to swap the response instead of how the request said. */
	public static final String RESWAP = "HX-Reswap";
}
