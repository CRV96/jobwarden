package com.jobwarden.discover.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.Decision;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A job found for a profile, and what became of it.
 *
 * @param source the site the posting is on, e.g. {@code jobs.lever.co}
 * @param searchName the name of the saved search it was found for, or what it was searched for
 * @param applicationId the application it became, once saved or applied to
 * @param requestId the request to Claude it was found for, if any
 * @param requestKind what that request asked for
 */
@Builder
public record FoundJob(long id, String title, @Nullable String company, @Nullable String location,
		@Nullable String salary, String link, @Nullable String source, @Nullable String description,
		@Nullable String note, @Nullable LocalDate postedOn, @Nullable Long searchId, @Nullable String searchName,
		Decision decision, Instant foundAt, @Nullable Instant decidedAt, @Nullable Long applicationId,
		@Nullable Long requestId, @Nullable AiRequestKind requestKind) {
}
