package com.jobwarden.cv.service.impl;

import java.nio.file.Path;

import com.jobwarden.common.secret.Secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts the GitHub tokens, with the key every module's secrets share: {@code jobwarden.secret-key}, else the key
 * file.
 */
@Component
class GitHubTokens extends Secrets {

	GitHubTokens(@Value("${jobwarden.secret-key:${jobwarden.inbox.secret-key:}}") String configuredKey,
			@Value("${jobwarden.key-file:${jobwarden.inbox.key-file:${user.home}/.jobwarden/inbox.key}}") Path keyFile) {
		super(configuredKey, keyFile);
	}

}
