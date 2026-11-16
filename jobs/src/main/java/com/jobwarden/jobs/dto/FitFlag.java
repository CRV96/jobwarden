package com.jobwarden.jobs.dto;

import java.time.LocalDate;

import com.jobwarden.jobs.enums.FitFlagKind;
import org.jspecify.annotations.Nullable;

/**
 * Something JobWarden noticed about a posting before applying, with what showed it.
 *
 * @param evidence for {@link FitFlagKind#NO_SPONSORSHIP}, the posting's words
 * @param since for {@link FitFlagKind#OPEN_LONG}, when the posting went up
 * @param otherApplicationId for {@link FitFlagKind#REPOSTED}, the same job saved before
 */
public record FitFlag(FitFlagKind kind, @Nullable String evidence, @Nullable LocalDate since,
		@Nullable Long otherApplicationId) {
}
