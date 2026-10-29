package com.jobwarden.hiring.dto;

import java.util.List;

/**
 * @param added the posts that are new to the profile
 * @param skipped the links it already had, hidden ones included
 */
public record AddedHiringPosts(List<HiringPost> added, List<String> skipped) {

	public AddedHiringPosts {
		added = List.copyOf(added);
		skipped = List.copyOf(skipped);
	}

}
