package com.jobwarden.hiring.dto;

import java.util.List;

import com.jobwarden.hiring.enums.RecruiterOrigin;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Someone who hires: a recruiter, a talent partner, a hiring manager.
 *
 * @param headline what they do, as their profile says, e.g. IT Recruiter at Acme
 * @param profileUrl their profile, e.g. on LinkedIn: who they are to JobWarden
 * @param company where they recruit, or the agency they recruit from
 * @param country where they recruit, as an ISO 3166 code
 * @param note why Claude suggests them
 * @param followed whether the profile follows them: their posts come first, and Claude looks at theirs first
 * @param posts how many of their posts the profile has, hidden ones left out
 * @param contactFor the profile's jobs they're the contact for
 */
@Builder
public record Recruiter(long id, String name, @Nullable String headline, String profileUrl, @Nullable String company,
		@Nullable String country, @Nullable String note, RecruiterOrigin origin, boolean followed, long posts,
		List<ContactJob> contactFor) {

	public Recruiter {
		origin = (origin != null) ? origin : RecruiterOrigin.POST;
		contactFor = (contactFor != null) ? List.copyOf(contactFor) : List.of();
	}

}
