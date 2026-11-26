package com.jobwarden.jobs.service;

import java.util.Optional;

import com.jobwarden.jobs.dto.CompanyResearch;

/**
 * What the AI Assistant found out on the web about a job's company: one research per job, which its page shows. Every
 * method takes the owning user's id, like {@link ApplicationService}.
 */
public interface CompanyResearchService {

	Optional<CompanyResearch> find(long userId, long applicationId);

	/**
	 * Keeps the research, replacing the one the job had.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @return the research as kept, with when
	 */
	CompanyResearch save(long userId, long applicationId, CompanyResearch research);

	void delete(long userId, long applicationId);

}
