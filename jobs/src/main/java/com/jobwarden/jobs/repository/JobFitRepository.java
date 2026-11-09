package com.jobwarden.jobs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.entity.JobFitEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobFitRepository extends JpaRepository<JobFitEntity, Long> {

	Optional<JobFitEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	/**
	 * The scores of these applications of the profile, without their requirements: what the list and the pipeline
	 * show next to each job.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.FitScore(f.applicationId, f.score)
			from JobFitEntity f
			where f.userId = :userId and f.applicationId in :applicationIds""")
	List<FitScore> findScores(long userId, Collection<Long> applicationIds);

}
