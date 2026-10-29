package com.jobwarden.hiring.dto;

import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A recruiter Claude found, to add. Build it with {@link #builder()}.
 *
 * @param profileUrl their profile, e.g. on LinkedIn: who they are to JobWarden
 * @param company where they recruit, or the agency they recruit from
 * @param country where they recruit, as an ISO 3166 code, e.g. RO; anything else is left out
 * @param note why they're worth following, in a sentence
 */
@Builder
public record NewRecruiter(String name, @Nullable String headline, String profileUrl, @Nullable String company,
		@Nullable String country, @Nullable String note) {

	public NewRecruiter {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(profileUrl, "profileUrl");
	}

}
