package com.jobwarden.identity.dto;

import java.time.Instant;
import java.util.UUID;

import com.jobwarden.identity.enums.Assistant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTests {

	private static final User.UserBuilder ROBERT = User.builder()
		.id(1)
		.name("Robert")
		.createdAt(Instant.parse("2026-09-28T10:00:00Z"))
		.assistant(Assistant.CLAUDE_CODE)
		.publicId(UUID.randomUUID());

	@Test
	void hasAPhotoOnceOneWasEverSet() {
		assertThat(ROBERT.build().hasImage()).isFalse();
		assertThat(ROBERT.imageChangedAt(Instant.parse("2026-10-01T08:00:00Z")).build().hasImage()).isTrue();
	}

}
