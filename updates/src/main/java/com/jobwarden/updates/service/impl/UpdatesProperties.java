package com.jobwarden.updates.service.impl;

import java.net.URI;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where JobWarden looks for a newer release of itself: {@code jobwarden.updates.*}.
 *
 * @param repository the GitHub repository JobWarden is released from, e.g. CRV96/jobwarden
 * @param check whether to ask GitHub at all; {@code false} never does
 * @param api GitHub's API, which the tests point at a server of their own
 */
@ConfigurationProperties("jobwarden.updates")
@Validated
record UpdatesProperties(
		@DefaultValue("CRV96/jobwarden") @Pattern(regexp = "[\\w.-]+/[\\w.-]+",
				message = "must be a GitHub repository, as owner/name") String repository,
		@DefaultValue("true") boolean check, @DefaultValue("https://api.github.com") @NotNull URI api) {
}
