package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.time.LocalDateTime;

import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.enums.ReminderLead;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * A reminder of a calendar entry: an event of a job's timeline, its deadline, or the day to follow up on it. Plain
 * ids, like {@link CompanyLogoEntity}'s, so loading an application never loads its reminders.
 */
@Entity
@Table(name = "reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ReminderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	@Enumerated(EnumType.STRING)
	private CalendarEntryKind kind;

	// Only for an event of the timeline
	private @Nullable Long eventId;

	@Enumerated(EnumType.STRING)
	private ReminderLead remindBefore;

	// The entry's start it last came for: none until it comes
	private @Nullable LocalDateTime notifiedFor;

	@CreationTimestamp
	private Instant createdAt;

	public ReminderEntity(long userId, long applicationId, CalendarEntryKind kind, @Nullable Long eventId,
			ReminderLead remindBefore) {
		this.userId = userId;
		this.applicationId = applicationId;
		this.kind = kind;
		this.eventId = eventId;
		this.remindBefore = remindBefore;
	}

}
