package com.jobwarden.identity.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A profile. In the self-hosted version it has no password: anyone with access to the machine
 * already has full access to the database, so there is no real permission boundary to enforce.
 */
@Builder
public record User(long id, String name, Instant createdAt, @Nullable Instant imageChangedAt) {

	/**
	 * A profile without a photo.
	 */
	public User(long id, String name, Instant createdAt) {
		this(id, name, createdAt, null);
	}

	public boolean hasImage() {
		return this.imageChangedAt != null;
	}
}
