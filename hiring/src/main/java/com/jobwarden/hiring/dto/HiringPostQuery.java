package com.jobwarden.hiring.dto;

import com.jobwarden.hiring.enums.PostedWithin;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Which posts to show. Every field is optional. Build it with {@link #builder()}.
 *
 * @param search words the post, its role, its company or its author has, e.g. java
 * @param country an ISO 3166 code
 * @param tag a hashtag, without the #
 * @param followedOnly only the posts of the recruiters the profile follows
 * @param recruiterId only this recruiter's posts
 */
@Builder
public record HiringPostQuery(@Nullable String search, PostedWithin postedWithin, @Nullable String country,
		@Nullable String tag, boolean followedOnly, @Nullable Long recruiterId) {

	/** Every post. */
	public static final HiringPostQuery ALL = HiringPostQuery.builder().build();

	public HiringPostQuery {
		postedWithin = (postedWithin != null) ? postedWithin : PostedWithin.ANY;
	}

}
