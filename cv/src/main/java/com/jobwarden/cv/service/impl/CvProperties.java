package com.jobwarden.cv.service.impl;

import java.net.URI;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The CV module's settings: {@code jobwarden.cv.*}.
 *
 * @param githubApi GitHub's API, where the CV's projects come from; the tests point it at a server of their own
 */
@ConfigurationProperties("jobwarden.cv")
@Validated
record CvProperties(@DefaultValue("https://api.github.com") @NotNull URI githubApi) {
}
