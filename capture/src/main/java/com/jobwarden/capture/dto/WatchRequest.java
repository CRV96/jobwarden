package com.jobwarden.capture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Watching the company whose job board the page is on.
 *
 * @param link the page: one of the company's jobs, or its board, on Greenhouse, Lever or Ashby
 * @param name the company's name, as the page says it; the board's own if left out
 */
public record WatchRequest(@NotNull Long userId, @NotBlank @Size(max = WatchRequest.LINK_MAX_LENGTH) String link,
		@Size(max = 200) @Nullable String name) {

	/** Longer than any job's address. */
	public static final int LINK_MAX_LENGTH = 2048;

}
