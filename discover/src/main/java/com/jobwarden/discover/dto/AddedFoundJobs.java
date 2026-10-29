package com.jobwarden.discover.dto;

import java.util.List;

/**
 * What adding found jobs did.
 *
 * @param added the jobs that are now waiting on the AI Assistant page
 * @param skipped the links left out, and why: already found, already an application, or not a web address
 */
public record AddedFoundJobs(List<FoundJob> added, List<SkippedLink> skipped) {

	public AddedFoundJobs {
		added = List.copyOf(added);
		skipped = List.copyOf(skipped);
	}

	public record SkippedLink(String link, String reason) {
	}

}
