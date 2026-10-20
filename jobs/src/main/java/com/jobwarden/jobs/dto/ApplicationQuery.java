package com.jobwarden.jobs.dto;

import java.util.Objects;

import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Sort;

/**
 * Which page of the application list to show. Build it with {@link #builder()}.
 *
 * @param search only applications whose title or company contains this text, ignoring case; {@code null} or blank
 * for all
 * @param status only applications with this status; {@code null} for all
 * @param page the page number, from 0
 * @param size how many applications a page holds
 */
@Builder
public record ApplicationQuery(
		@Nullable String search,
		@Nullable ApplicationStatus status,
		ApplicationSort sort,
		Sort.Direction direction,
		int page,
		int size) {

	/**
	 * A builder can't make sure every required field is set, so a missing or impossible one fails here, right away.
	 */
	public ApplicationQuery {
		Objects.requireNonNull(sort, "sort");
		Objects.requireNonNull(direction, "direction");
		if (page < 0 || size < 1) {
			throw new IllegalArgumentException("No page " + page + " of size " + size);
		}
	}

}
