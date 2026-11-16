package com.jobwarden.watchlist.dto;

import java.time.Instant;

import com.jobwarden.watchlist.enums.Ats;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A company the profile watches, by its job board.
 *
 * @param board the board's name on its system, e.g. acme
 * @param boardUrl where people see the board, e.g. https://jobs.lever.co/acme
 * @param lastCheckedAt when JobWarden last read the board, or tried to
 * @param lastError why it couldn't, that last time; {@code null} when it could
 * @param newThisWeek how many jobs from its board went to the AI Assistant page in the last seven days
 */
@Builder
public record WatchedCompany(long id, String name, Ats ats, String board, String boardUrl, Instant addedAt,
		@Nullable Instant lastCheckedAt, @Nullable String lastError, long newThisWeek) {
}
