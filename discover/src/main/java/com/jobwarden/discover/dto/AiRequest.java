package com.jobwarden.discover.dto;

import java.time.Instant;

import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Something the profile asked Claude to do, and how it went.
 *
 * @param summary what Claude did, or why it couldn't, once it's finished
 * @param foundJobs how many jobs Claude added for it
 * @param startedAt when Claude picked it up
 * @param applicationId the job it's about, for a kind that {@link AiRequestKind#isAboutAJob() is about one}
 */
@Builder
public record AiRequest(long id, AiRequestKind kind, AiRequirements requirements, AiRequestStatus status,
		@Nullable String summary, long foundJobs, Instant createdAt, @Nullable Instant startedAt,
		@Nullable Instant finishedAt, @Nullable Long applicationId) {
}
