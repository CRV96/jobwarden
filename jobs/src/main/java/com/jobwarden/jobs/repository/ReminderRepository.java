package com.jobwarden.jobs.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.entity.ReminderEntity;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import org.jspecify.annotations.Nullable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReminderRepository extends JpaRepository<ReminderEntity, Long> {

	List<ReminderEntity> findByUserId(long userId);

	List<ReminderEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	/**
	 * Written out, as a method name ending in RemindBefore would read as a comparison of dates. Ids start at 1, so 0
	 * stands for no event in both.
	 *
	 * @param eventId {@code null} matches the reminders of a deadline or a day to follow up, which have none
	 */
	@Query("""
			select r from ReminderEntity r
			where r.userId = :userId and r.applicationId = :applicationId and r.kind = :kind
			    and coalesce(r.eventId, 0) = coalesce(:eventId, 0) and r.remindBefore = :lead""")
	Optional<ReminderEntity> findReminder(long userId, long applicationId, CalendarEntryKind kind, @Nullable Long eventId,
			ReminderLead lead);

	/**
	 * Marks the reminder as come for its entry's start, in one statement, so that of two callers asking at once (two
	 * tabs, or a tab and the extension) only one gets it.
	 *
	 * @return 1 if it was this call's to give, 0 if it already came for that start
	 */
	@Modifying
	@Query("""
			update ReminderEntity r set r.notifiedFor = :start
			where r.id = :id and (r.notifiedFor is null or r.notifiedFor <> :start)""")
	int markNotified(long id, LocalDateTime start);

}
