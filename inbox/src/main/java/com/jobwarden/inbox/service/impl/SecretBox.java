package com.jobwarden.inbox.service.impl;

import java.nio.file.Path;

import com.jobwarden.common.secret.Secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts the mailboxes' passwords, with the key every module's secrets share: {@code jobwarden.secret-key}, else the
 * key file. The inbox's own names for them, from before others kept secrets, still count.
 */
@Component
class SecretBox extends Secrets {

	SecretBox(@Value("${jobwarden.secret-key:${jobwarden.inbox.secret-key:}}") String configuredKey,
			@Value("${jobwarden.key-file:${jobwarden.inbox.key-file:${user.home}/.jobwarden/inbox.key}}") Path keyFile) {
		super(configuredKey, keyFile);
	}

}
