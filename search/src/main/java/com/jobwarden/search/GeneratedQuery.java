package com.jobwarden.search;

import org.jspecify.annotations.Nullable;

/**
 * A query, and the address that runs it.
 *
 * @param query empty when there's nothing to search for on the platform
 * @param searchUrl {@code null} with an empty query
 */
public record GeneratedQuery(Platform platform, String query, @Nullable String searchUrl) {
}
