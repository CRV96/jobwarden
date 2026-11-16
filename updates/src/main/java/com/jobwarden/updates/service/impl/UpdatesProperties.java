package com.jobwarden.updates.service.impl;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where and how often JobWarden looks for a newer release of itself: {@code jobwarden.updates.*}. How often it looks
 * whether a check is due ({@code tick}) is the schedule's, in DefaultUpdateService.
 *
 * @param repository the GitHub repository JobWarden is released from, e.g. CRV96/jobwarden
 * @param check whether to ask GitHub at all; {@code false} never does
 * @param every how long after a check GitHub is asked again: a day unless said otherwise
 * @param api GitHub's API, which the tests point at a server of their own
 */
@ConfigurationProperties("jobwarden.updates")
@Validated
record UpdatesProperties(
		@DefaultValue("CRV96/jobwarden") @Pattern(regexp = "[\\w.-]+/[\\w.-]+",
				message = "must be a GitHub repository, as owner/name") String repository,
		@DefaultValue("true") boolean check, @DefaultValue("P1D") @NotNull Duration every,
		@DefaultValue("https://api.github.com") @NotNull URI api) {
}
