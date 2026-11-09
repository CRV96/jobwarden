package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * A personal project on the CV, e.g. one of the person's GitHub repositories.
 *
 * @param link where to see it, e.g. its repository or its site
 * @param technologies what it's made with, in a few words, e.g. Java, Spring Boot, PostgreSQL
 * @param description what it does and what the person built in it, in a sentence or three
 */
public record CvProject(String name, @Nullable String link, @Nullable String technologies, @Nullable String description) {
}
