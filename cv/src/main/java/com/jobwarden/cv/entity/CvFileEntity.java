package com.jobwarden.cv.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A profile's uploaded CV file, one per profile, with the text read out of it.
 */
@Entity
@Table(name = "cv_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CvFileEntity {

	// The profile's id: a plain id, like the other modules' userId
	@Id
	private Long userId;

	private String fileName;

	private String contentType;

	private byte[] content;

	private long sizeBytes;

	private @Nullable String text;

	private Instant uploadedAt;

	public CvFileEntity(long userId) {
		this.userId = userId;
	}

	/**
	 * @param text what could be read out of it; {@code null} when nothing could
	 */
	public void replace(String fileName, String contentType, byte[] content, @Nullable String text) {
		this.fileName = fileName;
		this.contentType = contentType;
		this.content = content;
		this.sizeBytes = content.length;
		this.text = text;
		this.uploadedAt = Instant.now();
	}

}
