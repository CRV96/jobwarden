package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.List;

/**
 * The profile's GitHub account, without its token.
 *
 * @param hasToken whether the profile gave a token, which reads the private repositories it was given for too
 * @param repositories the repositories the profile chose for Claude to write the CV's projects from, as owner/name
 */
public record GitHubAccount(String username, boolean hasToken, List<String> repositories, Instant connectedAt) {

	public GitHubAccount {
		repositories = List.copyOf(repositories);
	}

}
