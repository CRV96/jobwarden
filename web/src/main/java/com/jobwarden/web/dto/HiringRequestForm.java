package com.jobwarden.web.dto;

import java.util.Arrays;
import java.util.List;

import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.search.Recency;
import org.jspecify.annotations.Nullable;

/**
 * What the Who's hiring page asks Claude for, posts or recruiters, and what to look for: roles and places, each a
 * comma-separated list; for posts, how recent they must be; for recruiters, how many to find.
 *
 * @param kind {@link AiRequestKind#FIND_HIRING_POSTS} or {@link AiRequestKind#FIND_RECRUITERS}; posts when it's
 * anything else
 * @param roles e.g. Java Developer, Backend Engineer
 * @param places e.g. Romania, Remote in Europe
 * @param count how many recruiters to find, at most
 */
public record HiringRequestForm(@Nullable AiRequestKind kind, @Nullable String roles, @Nullable String places,
		@Nullable Recency recency, @Nullable Integer count) {

	/** The counts the page offers for recruiters */
	public static final List<Integer> COUNTS = List.of(10, 20, 30);

	public HiringRequestForm {
		kind = (kind == AiRequestKind.FIND_RECRUITERS) ? kind : AiRequestKind.FIND_HIRING_POSTS;
	}

	public AiRequirements toRequirements() {
		AiRequirements.AiRequirementsBuilder requirements = AiRequirements.builder()
			.titles(list(this.roles))
			.locations(list(this.places));
		return (this.kind == AiRequestKind.FIND_RECRUITERS)
				? requirements.maxJobs((this.count != null && COUNTS.contains(this.count)) ? this.count : COUNTS.getFirst())
					.build()
				: requirements.recency((this.recency != null) ? this.recency : Recency.MONTH).build();
	}

	private static List<String> list(@Nullable String text) {
		return (text == null) ? List.of()
				: Arrays.stream(text.split(",")).map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
	}

}
