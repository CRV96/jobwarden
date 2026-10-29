package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * A profile's CV: the file uploaded, if any, and the fields filled in, {@link CvDetails#NONE} when none is.
 */
public record Cv(@Nullable CvFile file, CvDetails details) {
}
