package com.jobwarden.cv.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The profile's CV as Claude rewrote it for one job: what the person did, in the posting's words, in a form the
 * screening software employers run CVs through reads. Build it with {@link #builder()}.
 *
 * @param applicationId the job it's for
 * @param matchedKeywords the posting's skills and keywords the CV now names
 * @param missingKeywords those the posting asks for that the CV can't honestly claim
 * @param notes what Claude changed, and what to look at before sending it
 */
@Builder
public record TailoredCv(long applicationId, CvDetails details, List<String> matchedKeywords,
		List<String> missingKeywords, @Nullable String notes, Instant updatedAt) {

	public TailoredCv {
		matchedKeywords = (matchedKeywords != null) ? List.copyOf(matchedKeywords) : List.of();
		missingKeywords = (missingKeywords != null) ? List.copyOf(missingKeywords) : List.of();
	}

}
