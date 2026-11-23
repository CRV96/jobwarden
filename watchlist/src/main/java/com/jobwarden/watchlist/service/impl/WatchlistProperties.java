package com.jobwarden.watchlist.service.impl;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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
 * @param smartRecruitersApi SmartRecruiters'
 * @param workableApi Workable's
 * @param recruiteeApi Recruitee's, where {board} is the board's name: each board is a host of its own
 * @param personioApi Personio's, the same way
 */
@ConfigurationProperties("jobwarden.watchlist")
@Validated
record WatchlistProperties(@DefaultValue("PT6H") @NotNull Duration every,
		@DefaultValue("https://boards-api.greenhouse.io") @NotNull URI greenhouseApi,
		@DefaultValue("https://api.lever.co") @NotNull URI leverApi,
		@DefaultValue("https://api.eu.lever.co") @NotNull URI leverEuApi,
		@DefaultValue("https://api.ashbyhq.com") @NotNull URI ashbyApi,
		@DefaultValue("https://api.smartrecruiters.com") @NotNull URI smartRecruitersApi,
		@DefaultValue("https://apply.workable.com") @NotNull URI workableApi,
		@DefaultValue("https://{board}.recruitee.com") @NotNull @Pattern(regexp = ".*\\{board}.*") String recruiteeApi,
		@DefaultValue("https://{board}.jobs.personio.de") @NotNull @Pattern(regexp = ".*\\{board}.*") String personioApi) {

	/** Where a template has the board's name. */
	static final String BOARD = "{board}";

}
