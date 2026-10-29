package com.jobwarden.updates.dto;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTests {

	@Test
	void readsVersionsAndTags() {
		assertThat(Version.parse("0.2.0")).contains(new Version(List.of(0, 2, 0), null));
		assertThat(Version.parse(" v1.10 ")).contains(new Version(List.of(1, 10), null));
		assertThat(Version.parse("1.5.0-SNAPSHOT")).contains(new Version(List.of(1, 5, 0), "SNAPSHOT"));
		assertThat(Version.parse("dev")).isEmpty();
		assertThat(Version.parse("@project.version@")).isEmpty();
		assertThat(Version.parse(null)).isEmpty();
	}

	@Test
	void comparesTheNumbersInTurnAndAReleaseAfterItsPreReleases() {
		assertThat(Version.isNewer("0.10.0", "0.9.3")).isTrue();
		assertThat(Version.isNewer("v1.0", "0.99.99")).isTrue();
		assertThat(Version.isNewer("1.4.1", "1.4")).isTrue();
		assertThat(Version.isNewer("1.4.0", "1.4")).isFalse();
		assertThat(Version.isNewer("1.5.0", "1.5.0-SNAPSHOT")).isTrue();
		assertThat(Version.isNewer("1.5.0-SNAPSHOT", "1.4.9")).isTrue();
		assertThat(Version.isNewer("0.1.0", "0.1.0")).isFalse();
		assertThat(Version.isNewer("0.2.0", "dev")).isFalse();
		assertThat(Version.parse("1.4.0").orElseThrow().toString()).isEqualTo("1.4.0");
	}

}
