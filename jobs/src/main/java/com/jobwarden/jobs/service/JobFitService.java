package com.jobwarden.jobs.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.FitFlag;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.JobFit;
import org.jspecify.annotations.Nullable;

/**
 * How well the profile fits its jobs, to apply better to fewer: Claude reads each posting's requirements against the
 * CV, and JobWarden works out the score from them, the same way for every job ({@link JobFit#score}). Next to it, what
 * JobWarden notices on its own, with no AI: a posting that rules out sponsorship, one posted again, one up for long.
 * Every method takes the owning user's id, like {@link ApplicationService}.
 */
public interface JobFitService {

	/** After how many days up a posting counts as up for long. */
	int OPEN_LONG_AFTER_DAYS = 60;

	Optional<JobFit> find(long userId, long applicationId);

	/**
	 * Keeps the job's fit, replacing the one it had, with the score worked out from the requirements.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 * @throws IllegalArgumentException with no requirements
	 */
	JobFit save(long userId, long applicationId, @Nullable String summary, List<FitRequirement> requirements);

	void delete(long userId, long applicationId);

	/**
	 * @return the scores of those of the jobs that have one, by job: for the list and the pipeline, in one query
	 */
	Map<Long, BigDecimal> findScores(long userId, Collection<Long> applicationIds);

	/**
	 * @return the jobs still to apply to or waiting on an answer that have no fit yet, the newest first
	 */
	List<ApplicationSummary> findUnscored(long userId, int limit);

	/**
	 * What JobWarden notices about the job's posting on its own.
	 *
	 * @param needsSponsorship whether the profile needs a visa sponsored, from its Auto-apply answers; {@code null}
	 * when it didn't say, and then no posting is flagged for it
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 */
	List<FitFlag> findFlags(long userId, long applicationId, @Nullable Boolean needsSponsorship);

}
