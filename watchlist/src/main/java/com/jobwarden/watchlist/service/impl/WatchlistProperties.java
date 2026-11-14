package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How the watched companies' job boards are read: {@code jobwarden.watchlist.*}. How often JobWarden looks for the
 * boards whose time has come ({@code tick}, {@code first-check}) is the schedule's, in WatchlistChecks.
 *
 * @param every how long a board is left before it's read again
 * @param greenhouseApi Greenhouse's job board API; the addresses are the tests' to change
 * @param leverApi Lever's, for boards in the US
 * @param leverEuApi Lever's, for boards in the EU
 * @param ashbyApi Ashby's
 */
@ConfigurationProperties("jobwarden.watchlist")
@Validated
record WatchlistProperties(@DefaultValue("PT6H") @NotNull Duration every,
		@DefaultValue("https://boards-api.greenhouse.io") @NotNull URI greenhouseApi,
		@DefaultValue("https://api.lever.co") @NotNull URI leverApi,
		@DefaultValue("https://api.eu.lever.co") @NotNull URI leverEuApi,
		@DefaultValue("https://api.ashbyhq.com") @NotNull URI ashbyApi) {
}
