package com.jobwarden.jobs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.enums.ApplicationStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long>, ApplicationQueries {

	/**
	 * The pipeline's cards. Selects only the columns the pipeline shows: the description and the raw payload can be
	 * large, and loading them for every card would be wasted work.
	 */
	@Query("""
			select new com.jobwarden.jobs.dto.ApplicationSummary(
			    a.id, a.title, a.company, a.location, a.salary, a.source, a.currentStatus, a.capturedAt)
			from ApplicationEntity a
			where a.userId = :userId
			order by a.capturedAt desc""")
	List<ApplicationSummary> findSummaries(long userId);

	/**
	 * Like {@link #findSummaries(long)}, only for applications with this status.
	 */
	@Query("""
			select new com.jobwarden.jobs.dto.ApplicationSummary(
			    a.id, a.title, a.company, a.location, a.salary, a.source, a.currentStatus, a.capturedAt)
			from ApplicationEntity a
			where a.userId = :userId and a.currentStatus = :status
			order by a.capturedAt desc""")
	List<ApplicationSummary> findSummaries(long userId, ApplicationStatus status);

	long countByUserId(long userId);

	/**
	 * Loads the timeline in the same query: every caller (the detail page, adding an event, deleting) needs it.
	 */
	@EntityGraph(attributePaths = "events")
	Optional<ApplicationEntity> findByIdAndUserId(long id, long userId);

	/**
	 * With the timeline, like {@link #findByIdAndUserId}: capturing may add an event to it, and looking a link up
	 * shows when its status changed last.
	 */
	@EntityGraph(attributePaths = "events")
	Optional<ApplicationEntity> findFirstByUserIdAndLink(long userId, String link);

	/**
	 * In one statement, without loading the applications first. Their timelines go with them: the database deletes
	 * the events of a deleted application ({@code ON DELETE CASCADE}).
	 *
	 * @return how many applications were deleted
	 */
	@Modifying
	@Query("delete from ApplicationEntity a where a.userId = :userId and a.id in :ids")
	int deleteOwned(long userId, Collection<Long> ids);

}
