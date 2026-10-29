package com.jobwarden.hiring.dto;

import java.time.LocalDate;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A post to add, with its author. Build it with {@link #builder()}.
 *
 * @param country an ISO 3166 code, e.g. RO; anything else is left out
 * @param authorProfileUrl the author's profile: the same one is the same recruiter
 */
@Builder
public record NewHiringPost(String link, String text, @Nullable String role, @Nullable String company,
		@Nullable String country, @Nullable LocalDate postedOn, String authorName, @Nullable String authorHeadline,
		String authorProfileUrl) {

	public NewHiringPost {
		Objects.requireNonNull(link, "link");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(authorName, "authorName");
		Objects.requireNonNull(authorProfileUrl, "authorProfileUrl");
	}

}
