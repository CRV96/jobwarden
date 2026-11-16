package com.jobwarden.market.service.impl;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the market salaries come from: {@code jobwarden.market.*}. The survey and Eurostat have no default address:
 * application.properties names the edition the page credits.
 *
 * @param stackOverflow the Stack Overflow Developer Survey's answers
 * @param eurostat Eurostat's earnings of professionals in information and communication
 * @param adzuna salaries from current job ads
 */
@ConfigurationProperties("jobwarden.market")
@Validated
record MarketProperties(@NotNull @Valid StackOverflow stackOverflow, @NotNull @Valid Eurostat eurostat,
		@DefaultValue @Valid Adzuna adzuna) {

	/**
	 * @param url the survey's CSV file, downloaded when the page's button asks for it; a {@code file:} address works too
	 * @param year the survey's edition, which the page credits
	 */
	record StackOverflow(@NotNull URI url, @Min(2011) int year) {
	}

	/**
	 * @param url the dataset's address in Eurostat's API
	 */
	record Eurostat(@NotNull URI url) {
	}

	/**
	 * @param url Adzuna's API
	 * @param appId the key's id, from developer.adzuna.com; a key saved in Settings takes the place of this one
	 * @param appKey the key itself
	 */
	record Adzuna(@DefaultValue("https://api.adzuna.com/v1/api") @NotNull URI url, @DefaultValue("") String appId,
			@DefaultValue("") String appKey) {
	}

}
