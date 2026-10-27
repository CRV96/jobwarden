package com.jobwarden.discover.dto;

import java.time.Instant;

import com.jobwarden.search.SearchCriteria;

/**
 * A search kept for finding jobs: the Boolean search's filters, under a name.
 */
public record SavedSearch(long id, String name, SearchCriteria criteria, Instant createdAt) {
}
