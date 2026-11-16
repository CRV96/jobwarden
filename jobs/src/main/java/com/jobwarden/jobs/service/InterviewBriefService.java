package com.jobwarden.jobs.service;

import java.util.Optional;

import com.jobwarden.jobs.dto.InterviewBrief;

/**
 * What Claude found out to prepare the profile for a job's interview: one brief per job, which its page shows. Every
 * method takes the owning user's id, like {@link ApplicationService}.
 */
public interface InterviewBriefService {

	Optional<InterviewBrief> find(long userId, long applicationId);

	/**
	 * Keeps the brief, replacing the one the job had.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @return the brief as kept, with when
	 */
	InterviewBrief save(long userId, long applicationId, InterviewBrief brief);

	void delete(long userId, long applicationId);

}
