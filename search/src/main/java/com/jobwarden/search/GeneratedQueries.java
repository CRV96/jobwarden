package com.jobwarden.search;

import java.util.List;

/**
 * A query for each platform, in the order of the platforms.
 *
 * @param googleWords how many words the Google query has: Google reads {@link #GOOGLE_WORD_LIMIT} at most
 * @param googleTruncated whether terms were left out of the Google query to keep it within the limit
 */
public record GeneratedQueries(List<GeneratedQuery> all, int googleWords, boolean googleTruncated) {

	public static final int GOOGLE_WORD_LIMIT = 32;

	public GeneratedQueries {
		all = List.copyOf(all);
	}

	public GeneratedQuery get(Platform platform) {
		return this.all.stream()
			.filter(query -> query.platform() == platform)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No query for " + platform));
	}

}
