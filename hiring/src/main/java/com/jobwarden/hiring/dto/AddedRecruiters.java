package com.jobwarden.hiring.dto;

import java.util.List;

/**
 * @param added the recruiters that are new to the profile
 * @param skipped the profiles it already had, removed ones included, and those that aren't web addresses
 */
public record AddedRecruiters(List<Recruiter> added, List<String> skipped) {

	public AddedRecruiters {
		added = List.copyOf(added);
		skipped = List.copyOf(skipped);
	}

}
