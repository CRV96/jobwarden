package com.jobwarden.cv.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A CV rewritten for one job. Its fields are one JSON document, written and read by the service, like the lists of
 * {@link CvDetailsEntity}.
 */
@Entity
@Table(name = "tailored_cvs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TailoredCvEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	@JdbcTypeCode(SqlTypes.JSON)
	private String details;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> matchedKeywords = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> missingKeywords = new ArrayList<>();

	private @Nullable String notes;

	private Instant updatedAt;

	public TailoredCvEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * @param details the fields as a JSON document
	 */
	public void replace(String details, List<String> matchedKeywords, List<String> missingKeywords,
			@Nullable String notes) {
		this.details = details;
		this.matchedKeywords = new ArrayList<>(matchedKeywords);
		this.missingKeywords = new ArrayList<>(missingKeywords);
		this.notes = notes;
		this.updatedAt = Instant.now();
	}

}
