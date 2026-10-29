package com.jobwarden.cv.repository;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * An uploaded file without its content, selected straight into a record: the page shows it without loading the file.
 */
public record CvFileSummary(String fileName, String contentType, Long sizeBytes, @Nullable Integer textLength,
		Instant uploadedAt) {
}
