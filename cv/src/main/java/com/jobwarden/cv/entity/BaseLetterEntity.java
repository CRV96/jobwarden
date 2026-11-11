package com.jobwarden.cv.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

	/**
	 * In microseconds, as the database keeps it: the letter saved is the letter read back, on any clock.
	 */
	public void changeBody(String body) {
		this.body = body;
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

}
