package com.jobwarden.apply.dto;

import java.time.Instant;

import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A job queued for Claude to apply to, and how it went.
 *
 * @param link the posting, where the form is
 * @param site the form it was on, e.g. Greenhouse, once Claude says
 * @param note what Claude says happened
 * @param question what the form asked that the answers don't say, when it's {@link ApplyStatus#NEEDS_ANSWER}
 */
@Builder
public record ApplyItem(long id, long applicationId, String title, @Nullable String company, @Nullable String link,
		ApplyMode mode, ApplyStatus status, @Nullable String site, @Nullable String note, @Nullable String question,
		Instant createdAt, @Nullable Instant finishedAt) {
}
