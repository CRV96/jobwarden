package com.jobwarden.cv.dto;

/**
 * An uploaded CV file, to download.
 */
public record CvFileContent(String fileName, String contentType, byte[] content) {
}
