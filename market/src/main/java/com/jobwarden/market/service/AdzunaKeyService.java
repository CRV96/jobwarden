package com.jobwarden.market.service;

import com.jobwarden.market.dto.AdzunaKey;
import com.jobwarden.market.enums.AdzunaKeyCheck;

/**
 * The app's key for Adzuna's API, which the Salaries page asks for the salaries in current job ads: the one saved in
 * Settings, else the one in the ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables. Not a profile's: the same for
 * everyone, and in use as soon as it's saved.
 */
public interface AdzunaKeyService {

	/**
	 * @return the key in use, with only the end of its app key; {@link AdzunaKey#NONE} without one
	 */
	AdzunaKey find();

	/**
	 * Asks Adzuna with the key first, and saves it unless Adzuna turns it down. What the Salaries page asked with the
	 * key before is asked again.
	 *
	 * @return what Adzuna said: nothing is saved when it's {@link AdzunaKeyCheck#REFUSED}
	 * @throws IllegalArgumentException if the app ID or the app key is blank
	 */
	AdzunaKeyCheck save(String appId, String appKey);

	/**
	 * Removes the key saved in Settings: the environment's is in use again, if the server has one.
	 */
	void remove();

}
