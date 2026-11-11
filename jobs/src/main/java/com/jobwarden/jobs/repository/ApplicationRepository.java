package com.jobwarden.jobs.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.JobContact;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.enums.ApplicationStatus;

import org.springframework.data.domain.Pageable;
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
			where a.userId = :userId and a.currentStatus = :status and a.archivedAt is null
			order by a.capturedAt desc""")
	List<ApplicationSummary> findSummaries(long userId, ApplicationStatus status);

	/**
	 * @return which of these applications of the profile are archived
	 */
	@Query("select a.id from ApplicationEntity a where a.userId = :userId and a.id in :ids and a.archivedAt is not null")
	List<Long> findArchivedIds(long userId, Collection<Long> ids);

	/** The applications in use: the archived ones left out. */
	long countByUserIdAndArchivedAtIsNull(long userId);

	long countByUserIdAndArchivedAtIsNotNull(long userId);

	/**
	 * Archives those of these applications the profile owns and hasn't archived yet.
	 *
	 * @return how many it archived
	 */
	@Modifying
	@Query("""
			update ApplicationEntity a set a.archivedAt = :when
			where a.userId = :userId and a.id in :ids and a.archivedAt is null""")
	int archiveOwned(long userId, Collection<Long> ids, Instant when);

	/**
	 * Brings those of these applications the profile owns back into use.
	 *
	 * @return how many came back
	 */
	@Modifying
	@Query("""
			update ApplicationEntity a set a.archivedAt = null
			where a.userId = :userId and a.id in :ids and a.archivedAt is not null""")
	int restoreOwned(long userId, Collection<Long> ids);

	/**
	 * The applications in use that nothing changed on since then (their timeline included), with these statuses.
	 */
	@Query("""
			select a.id from ApplicationEntity a
			where a.userId = :userId and a.archivedAt is null and a.updatedAt < :before and a.currentStatus in :statuses""")
	List<Long> findUnchangedSince(long userId, Instant before, Collection<ApplicationStatus> statuses);

	/**
	 * When the posting went up, as its job data (a schema.org JobPosting, the raw payload) says, e.g. 2026-08-01 or
	 * 2026-08-01T10:00:00Z; empty when it doesn't say.
	 */
	@Query(value = "select raw_payload ->> 'datePosted' from applications where id = :id and user_id = :userId",
			nativeQuery = true)
	Optional<String> findDatePosted(long userId, long id);

	/**
	 * The profile's other applications with this title at this company, ignoring case, the oldest first: the same job,
	 * posted again.
	 */
	@Query("""
			select a.id from ApplicationEntity a
			where a.userId = :userId and a.id <> :id and lower(a.title) = lower(:title)
			    and lower(a.company) = lower(:company)
			order by a.capturedAt asc""")
	List<Long> findSameJob(long userId, long id, String title, String company);

	/**
	 * The profile's applications with these statuses and no fit yet, the newest first: those Claude scores next.
	 */
	@Query("""
			select new com.jobwarden.jobs.dto.ApplicationSummary(
			    a.id, a.title, a.company, a.location, a.salary, a.link, a.source, a.currentStatus, a.capturedAt,
			    a.updatedAt, a.appliedBy)
			from ApplicationEntity a
			where a.userId = :userId and a.currentStatus in :statuses and a.archivedAt is null
			    and not exists (select f.id from JobFitEntity f where f.applicationId = a.id)
			order by a.capturedAt desc, a.id desc""")
	List<ApplicationSummary> findUnscored(long userId, Collection<ApplicationStatus> statuses, Pageable page);

	/**
	 * The profile's applications with this status, each with the day of its latest event, for tidying up those that
	 * got no answer.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.WaitingApplication(a.id, max(e.eventDate), a.followUpOn)
			from TimelineEventEntity e join e.application a
			where a.userId = :userId and a.currentStatus = :status and a.archivedAt is null
			group by a.id, a.followUpOn""")
	List<WaitingApplication> findWaiting(long userId, ApplicationStatus status);

	/**
	 * The timeline events of the profile's applications between the two days, both included, for the calendar.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.CalendarEvent(
			    e.id, e.eventDate, e.eventTime, a.id, a.title, a.company, e.stageCategory, e.stageLabel,
			    e.interviewFormat, e.interviewLocation, e.durationMinutes)
			from TimelineEventEntity e join e.application a
			where a.userId = :userId and e.eventDate between :from and :to and a.archivedAt is null""")
	List<CalendarEvent> findCalendarEvents(long userId, LocalDate from, LocalDate to);

	/**
	 * One application's timeline events from that day on, for its reminders.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.CalendarEvent(
			    e.id, e.eventDate, e.eventTime, a.id, a.title, a.company, e.stageCategory, e.stageLabel,
			    e.interviewFormat, e.interviewLocation, e.durationMinutes)
			from TimelineEventEntity e join e.application a
			where a.userId = :userId and a.id = :applicationId and e.eventDate >= :from""")
	List<CalendarEvent> findCalendarEventsFrom(long userId, long applicationId, LocalDate from);

	/**
	 * These timeline events of the profile's applications, for the reminders set on them.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.CalendarEvent(
			    e.id, e.eventDate, e.eventTime, a.id, a.title, a.company, e.stageCategory, e.stageLabel,
			    e.interviewFormat, e.interviewLocation, e.durationMinutes)
			from TimelineEventEntity e join e.application a
			where a.userId = :userId and e.id in :eventIds""")
	List<CalendarEvent> findCalendarEventsById(long userId, Collection<Long> eventIds);

	/**
	 * The profile's applications with a deadline or a day to follow up between the two days, both included.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.FollowUpDates(
			    a.id, a.title, a.company, a.currentStatus, a.deadline, a.followUpOn)
			from ApplicationEntity a
			where a.userId = :userId and a.archivedAt is null
			    and (a.deadline between :from and :to or a.followUpOn between :from and :to)""")
	List<FollowUpDates> findFollowUpDates(long userId, LocalDate from, LocalDate to);

	/**
	 * The follow-up dates of these applications of the profile, whether they have any or not, for their reminders.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.FollowUpDates(
			    a.id, a.title, a.company, a.currentStatus, a.deadline, a.followUpOn)
			from ApplicationEntity a
			where a.userId = :userId and a.id in :applicationIds""")
	List<FollowUpDates> findFollowUpDatesById(long userId, Collection<Long> applicationIds);

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
