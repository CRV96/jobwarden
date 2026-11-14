package com.jobwarden.watchlist.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.jobwarden.search.Words;
import com.jobwarden.search.WorkType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Reading the feeds' JSON, the same for each {@link BoardFeed}.
 */
final class FeedJson {

	static final String LOCATION = "location";

	/** How much of a description the matching reads: enough for the requirements, which come after the intro. */
	static final int DESCRIPTION_MAX_LENGTH = 15_000;

	private FeedJson() {
	}

	/** Lever's remote, hybrid, onsite (on-site); Ashby's Remote, Hybrid, OnSite. */
	static @Nullable WorkType workType(@Nullable String type) {
		return switch (Words.of(type).replace(" ", "")) {
			case "remote" -> WorkType.REMOTE;
			case "hybrid" -> WorkType.HYBRID;
			case "onsite" -> WorkType.ONSITE;
			default -> null;
		};
	}

	static @Nullable String shortened(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return (text.length() <= DESCRIPTION_MAX_LENGTH) ? text : text.substring(0, DESCRIPTION_MAX_LENGTH - 1) + "…";
	}

	static @Nullable String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return (value.isString() && !value.asString().isBlank()) ? value.asString().strip() : null;
	}

	static List<String> strings(JsonNode array) {
		return array.values().stream().filter(JsonNode::isString).map(JsonNode::asString).filter(s -> !s.isBlank()).toList();
	}

	/**
	 * @return the moment, e.g. 2026-10-12T13:48:36.560+00:00 or 2026-10-12T13:48:36Z; one without an offset as UTC;
	 * {@code null} for anything else
	 */
	static @Nullable Instant instant(@Nullable String text) {
		if (text == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		} catch (DateTimeParseException _) {
			// Without its offset: as UTC
			try {
				return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
			} catch (DateTimeParseException _) {
				return null;
			}
		}
	}

}
