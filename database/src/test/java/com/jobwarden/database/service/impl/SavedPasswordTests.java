package com.jobwarden.database.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedPasswordTests {

	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	private static final String OTHER_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

	@TempDir
	private Path folder;

	@Test
	void keepsThePasswordEncryptedUntilItsReadBack() throws IOException {
		SavedPassword saved = saved(KEY);
		assertThat(saved.read()).isEmpty();

		saved.save("a long new password");

		assertThat(saved.read()).contains("a long new password");
		assertThat(Files.readString(saved.file())).doesNotContain("password");
		// Nothing left beside it
		try (var files = Files.list(this.folder)) {
			assertThat(files).containsExactly(saved.file());
		}
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void letsOnlyItsOwnerReadIt() throws IOException {
		SavedPassword saved = saved(KEY);

		saved.save("a long new password");

		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(saved.file()))).isEqualTo("rw-------");
	}

	@Test
	void replacesTheOneBeforeAndIsDeleted() throws IOException {
		SavedPassword saved = saved(KEY);
		saved.save("the first password");

		saved.save("the second password");
		assertThat(saved.read()).contains("the second password");

		saved.delete();
		assertThat(saved.read()).isEmpty();
		assertThat(saved.file()).doesNotExist();
	}

	@Test
	void cantBeReadWithAnotherKey() throws IOException {
		saved(KEY).save("a long new password");

		SavedPassword withAnotherKey = saved(OTHER_KEY);

		assertThatThrownBy(withAnotherKey::read).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void makesItsFolderAndAKeyFileWhenNoKeyIsSet() throws IOException {
		Path file = this.folder.resolve("jobwarden/database.password");
		SavedPassword saved = new SavedPassword(file, "", this.folder.resolve("jobwarden/inbox.key"));

		saved.save("a long new password");

		assertThat(saved.read()).contains("a long new password");
		assertThat(this.folder.resolve("jobwarden/inbox.key")).exists();
	}

	@Test
	void findsItsFileAndKeyInTheSettings() throws IOException {
		Path file = this.folder.resolve("database.password");
		MockEnvironment settings = new MockEnvironment().withProperty("jobwarden.database.password-file", file.toString())
			.withProperty("jobwarden.secret-key", KEY);

		SavedPassword.of(settings).save("a long new password");

		assertThat(saved(KEY).read()).contains("a long new password");
	}

	private SavedPassword saved(String key) {
		return new SavedPassword(this.folder.resolve("database.password"), key, this.folder.resolve("inbox.key"));
	}

}
