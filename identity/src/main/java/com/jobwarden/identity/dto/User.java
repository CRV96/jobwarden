package com.jobwarden.identity.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.jobwarden.identity.enums.Assistant;

/**
 * A profile. In the self-hosted version it has no password: anyone with access to the machine
 * already has full access to the database, so there is no real permission boundary to enforce.
 */
@Builder
public record User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt, Assistant assistant,
		UUID publicId) {

	/**
	 * @param assistant the AI assistant it works with; the default one when it chose none
	 * @param publicId the id it shows, to copy into the browser extension and an assistant's settings: made when it was
	 * created; one made from its id when none is given
	 */
	public User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt, @Nullable Assistant assistant,
			@Nullable UUID publicId) {
		this.id = id;
		this.name = name;
		this.createdAt = createdAt;
		this.imageChangedAt = imageChangedAt;
		this.assistant = Objects.requireNonNullElse(assistant, Assistant.DEFAULT);
		this.publicId = (publicId != null) ? publicId
				: UUID.nameUUIDFromBytes(("profile-" + id).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * A profile with the public id made from its id.
	 */
	public User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt, @Nullable Assistant assistant) {
		this(id, name, createdAt, imageChangedAt, assistant, null);
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
