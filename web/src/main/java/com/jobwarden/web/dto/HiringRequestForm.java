package com.jobwarden.web.dto;

import java.util.Arrays;
import java.util.List;

import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.search.Recency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public record HiringRequestForm(@NotNull @Nullable AiRequestKind kind,
		@Size(max = HiringRequestForm.TEXT_MAX_LENGTH) @Nullable String roles,
		@Size(max = HiringRequestForm.TEXT_MAX_LENGTH) @Nullable String places,
		@Nullable Recency recency, @Nullable Integer count) {

	/** The most the roles and the places may have; also their fields' maxlength in hiring/hiring.html. */
	public static final int TEXT_MAX_LENGTH = 500;

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

	/** What the page asks the assistant for, once the form is valid. */
	public AiRequestKind requiredKind() {
		return FormValues.validated(this.kind, "kind");
	}

}
