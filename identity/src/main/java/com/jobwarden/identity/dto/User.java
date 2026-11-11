package com.jobwarden.identity.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

import com.jobwarden.identity.enums.Assistant;

/**
 * A profile. In the self-hosted version it has no password: anyone with access to the machine
 * already has full access to the database, so there is no real permission boundary to enforce.
 */
@Builder
public record User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt, Assistant assistant) {

	/**
	 * @param assistant the AI assistant it works with; the default one when it chose none
	 */
	public User {
		assistant = Objects.requireNonNullElse(assistant, Assistant.DEFAULT);
	}

	/**
	 * A profile with the default assistant.
	 */
	public User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt) {
		this(id, name, createdAt, imageChangedAt, Assistant.DEFAULT);
	}

	/**
	 * A profile without a photo, with the default assistant.
	 */
	public User(long id, String name, Instant createdAt) {
		this(id, name, createdAt, null);
	}

	public boolean hasImage() {
		return this.imageChangedAt != null;
	}
}
