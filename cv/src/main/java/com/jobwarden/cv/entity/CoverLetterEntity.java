package com.jobwarden.cv.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A cover letter for one job, like {@link TailoredCvEntity}.
 */
@Entity
@Table(name = "cover_letters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private String body;

	private @Nullable String notes;

	private Instant updatedAt;

	public CoverLetterEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * Claude wrote it again.
	 */
	public void replace(String body, @Nullable String notes) {
		this.body = body;
		this.notes = notes;
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	/**
	 * The profile changed the letter: Claude's notes stay.
	 */
	public void changeBody(String body) {
		this.body = body;
		this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

}
