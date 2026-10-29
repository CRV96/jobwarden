package com.jobwarden.cv.dto;

import java.time.Instant;

/**
 * An uploaded CV file, without its content.
 *
 * @param size in bytes
 * @param textLength how many characters of text were read out of it; 0 when none could be, e.g. a scanned PDF
 */
public record CvFile(String fileName, String contentType, long size, int textLength, Instant uploadedAt) {
}
