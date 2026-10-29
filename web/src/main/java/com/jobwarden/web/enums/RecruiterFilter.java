package com.jobwarden.web.enums;

import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.RecruiterOrigin;

/**
 * Which recruiters the Who's hiring page's Recruiters tab shows.
 */
public enum RecruiterFilter {

	ALL, FOLLOWED, FOUND, CONTACTS;

	public boolean shows(Recruiter recruiter) {
		return switch (this) {
			case ALL -> true;
			case FOLLOWED -> recruiter.followed();
			case FOUND -> recruiter.origin() == RecruiterOrigin.FOUND;
			case CONTACTS -> !recruiter.contactFor().isEmpty();
		};
	}

}
