package com.jobwarden.jobs.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.JobContact;
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
			    a.id, a.title, a.company, a.location, a.salary, a.link, a.source, a.currentStatus, a.capturedAt,
			    a.updatedAt, a.appliedBy)
			from ApplicationEntity a
			where a.userId = :userId
			order by a.capturedAt desc""")
	List<ApplicationSummary> findSummaries(long userId);

	/**
	 * Like {@link #findSummaries(long)}, only for applications with this status.
	 */
	@Query("""
			select new com.jobwarden.jobs.dto.ApplicationSummary(
			    a.id, a.title, a.company, a.location, a.salary, a.link, a.source, a.currentStatus, a.capturedAt,
			    a.updatedAt, a.appliedBy)
			from ApplicationEntity a
			where a.userId = :userId and a.currentStatus = :status
			order by a.capturedAt desc""")
	List<ApplicationSummary> findSummaries(long userId, ApplicationStatus status);

	long countByUserId(long userId);

	/**
	 * The timeline events of the profile's applications between the two days, both included, for the calendar.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.CalendarEvent(
			    e.eventDate, e.eventTime, a.id, a.title, a.company, e.stageCategory, e.stageLabel, e.interviewFormat,
			    e.interviewLocation, e.durationMinutes)
			from TimelineEventEntity e join e.application a
			where a.userId = :userId and e.eventDate between :from and :to""")
	List<CalendarEvent> findCalendarEvents(long userId, LocalDate from, LocalDate to);

	/**
	 * The profile's applications with a deadline or a day to follow up between the two days, both included.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.FollowUpDates(
			    a.id, a.title, a.company, a.currentStatus, a.deadline, a.followUpOn)
			from ApplicationEntity a
			where a.userId = :userId
			    and (a.deadline between :from and :to or a.followUpOn between :from and :to)""")
	List<FollowUpDates> findFollowUpDates(long userId, LocalDate from, LocalDate to);

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
	 * The contacts saved on the profile's jobs, the jobs added last first.
	 */
	@Query("""
			select new com.jobwarden.jobs.dto.JobContact(
			    a.id, a.title, a.company, a.contactName, a.contactRole, a.contactEmail, a.contactLink)
			from ApplicationEntity a
			where a.userId = :userId and (a.contactName is not null or a.contactLink is not null)
			order by a.capturedAt desc, a.id desc""")
	List<JobContact> findContacts(long userId);

	/**
	 * Every application of the profile with its timeline, the oldest first: what an export writes.
	 */
	@EntityGraph(attributePaths = "events")
	List<ApplicationEntity> findByUserIdOrderByCapturedAtAscIdAsc(long userId);

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
