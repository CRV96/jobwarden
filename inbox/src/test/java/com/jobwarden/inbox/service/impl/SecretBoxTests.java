package com.jobwarden.inbox.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SecretBoxTests {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	@TempDir
	Path folder;

	@Test
	void decryptsWhatItEncryptedAndNeverKeepsItAsTyped() {
		SecretBox box = new SecretBox(KEY, this.folder.resolve("unused.key"));

		String encrypted = box.encrypt("app password 1234");

		assertThat(encrypted).doesNotContain("1234").isNotEqualTo(box.encrypt("app password 1234"));
		assertThat(box.decrypt(encrypted)).isEqualTo("app password 1234");
		assertThat(this.folder.resolve("unused.key")).doesNotExist();
	}

	@Test
	void makesAKeyFileTheFirstTimeAndReadsItAfter() throws Exception {
		Path keyFile = this.folder.resolve("keys/inbox.key");
		String encrypted = new SecretBox("", keyFile).encrypt("secret");

		assertThat(keyFile).exists();
		assertThat(Files.getPosixFilePermissions(keyFile)).hasSize(2);
		assertThat(new SecretBox("", keyFile).decrypt(encrypted)).isEqualTo("secret");
	}

	@Test
	void refusesWhatAnotherKeyEncrypted() {
		String encrypted = new SecretBox("", this.folder.resolve("one.key")).encrypt("secret");

		assertThatIllegalStateException().isThrownBy(() -> new SecretBox(KEY, this.folder.resolve("other.key")).decrypt(encrypted));
	}

}
