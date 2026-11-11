package com.jobwarden.cv.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The profile's base letter: one per profile, so the profile's id is its own.
 */
@Entity
@Table(name = "base_letters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseLetterEntity {

	@Id
	private Long userId;

	private String body;

	private Instant updatedAt;

	public BaseLetterEntity(long userId) {
		this.userId = userId;
	}

	public void changeBody(String body) {
		this.body = body;
		this.updatedAt = Instant.now();
	}

}
