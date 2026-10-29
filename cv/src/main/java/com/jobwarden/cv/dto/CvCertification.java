package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * @param year when it was earned, as the CV shows it
 */
public record CvCertification(String name, @Nullable String issuer, @Nullable String year) {
}
