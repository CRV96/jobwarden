package com.jobwarden.cv.dto;

/**
 * An uploaded CV file, to download.
 */
// The bytes go as they are, to be written out: no two are ever compared, so the array's equals doesn't matter
@SuppressWarnings("ArrayRecordComponent")
public record CvFileContent(String fileName, String contentType, byte[] content) {
}
