package com.jobwarden.watchlist.service.impl;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedJsonTests {

	@Test
	void readsAMomentWithItsOffset() {
		assertThat(FeedJson.instant("2026-10-12T13:48:36.560+02:00")).isEqualTo(Instant.parse("2026-10-12T11:48:36.560Z"));
		assertThat(FeedJson.instant("2026-10-12T13:48:36Z")).isEqualTo(Instant.parse("2026-10-12T13:48:36Z"));
	}

	@Test
	void readsAMomentWithoutAnOffsetAsUtc() {
		// Left out as null, an old posting would pass a search for recent ones
		assertThat(FeedJson.instant("2020-01-01T00:00:00")).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
	}

	@Test
	void hasNoMomentForAnythingElse() {
		assertThat(FeedJson.instant("yesterday")).isNull();
		assertThat(FeedJson.instant(null)).isNull();
	}

}
