package com.jobwarden.market.service.impl;

import java.net.URI;

/**
 * The market module's settings for a test: the sources it doesn't read point nowhere.
 */
final class MarketPropertiesFixtures {

	private static final URI NOWHERE = URI.create("http://127.0.0.1:1/");

	private MarketPropertiesFixtures() {
	}

	static MarketProperties adzunaAt(URI api) {
		return new MarketProperties(new MarketProperties.StackOverflow(NOWHERE, 2025),
				new MarketProperties.Eurostat(NOWHERE), new MarketProperties.Adzuna(api, "", ""));
	}

	static MarketProperties eurostatAt(URI eurostat) {
		return new MarketProperties(new MarketProperties.StackOverflow(NOWHERE, 2025),
				new MarketProperties.Eurostat(eurostat), new MarketProperties.Adzuna(NOWHERE, "", ""));
	}

}
