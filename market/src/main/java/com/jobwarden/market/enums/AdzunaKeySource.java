package com.jobwarden.market.enums;

/**
 * Where the key the app asks Adzuna's API with comes from.
 */
public enum AdzunaKeySource {

	/** Saved in Settings: it takes the place of the environment's */
	SETTINGS,

	/** The server's ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables */
	ENVIRONMENT,

	/** No key: the Salaries page leaves job ads out */
	NONE

}
