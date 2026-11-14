package com.jobwarden.common.secret;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SecretsTests {

	private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	@TempDir
	Path folder;

	@Test
	void refusesAConfiguredKeyOfTheWrongSizeAsItsMade() {
		assertThatIllegalStateException().isThrownBy(() -> new Secrets("AAAA", this.folder.resolve("unused.key")))
			.withMessageContaining("32 bytes");
	}

	@Test
	void refusesAConfiguredKeyThatIsntBase64AsItsMade() {
		assertThatIllegalStateException().isThrownBy(() -> new Secrets("not base64!", this.folder.resolve("unused.key")))
			.withMessageContaining("Base64");
	}

	@Test
	void aConfiguredKeyNeverMakesAKeyFile() {
		Path keyFile = this.folder.resolve("unused.key");
		Secrets secrets = new Secrets(KEY, keyFile);

		assertThat(secrets.decrypt(secrets.encrypt("secret"))).isEqualTo("secret");
		assertThat(Files.exists(keyFile)).isFalse();
	}

}
