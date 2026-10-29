package com.jobwarden;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app and the browser extension are released together, under one version: a release's tag, the Maven projects'
 * version and the extension's manifest say the same, so each can tell the other, and GitHub's releases, apart.
 */
class VersionTests {

	@Test
	void theExtensionHasTheAppsVersion() throws IOException {
		Properties app = new Properties();
		try (InputStream properties = getClass().getResourceAsStream("/application.properties")) {
			app.load(properties);
		}
		String version = app.getProperty("jobwarden.version");

		// Maven filled it in from the project's version
		assertThat(version).matches("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.]+)?");
		assertThat(JsonMapper.builder().build().readTree(Path.of("..", "extension", "manifest.json").toFile())
			.path("version")
			.asString()).isEqualTo(version);
	}

}
