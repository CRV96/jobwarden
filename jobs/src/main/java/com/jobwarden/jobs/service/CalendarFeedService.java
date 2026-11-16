package com.jobwarden.jobs.service;

import java.util.Optional;

/**
 * The secret in the address calendar apps subscribe to a profile's calendar at: Apple Calendar, Outlook, Google
 * Calendar and the like ask for it with no session, so the address itself says whose calendar it is. Whoever has it
 * reads the calendar, nothing more.
 */
public interface CalendarFeedService {

	/**
	 * @return the profile's secret, made the first time it's asked for
	 */
	String token(long userId);

	/**
	 * Makes a new secret, for when the address got where it shouldn't: the old one stops working.
	 *
	 * @return the new one
	 */
	String renewToken(long userId);

	/**
	 * @return the profile whose secret it is; empty for one that isn't, or no longer is
	 */
	Optional<Long> findOwner(String token);

}
