package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.RejectionReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "application_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class TimelineEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id")
	private ApplicationEntity application;

	private LocalDate eventDate;

	// Only when it's known; without one the event counts as the start of its day
	private @Nullable LocalTime eventTime;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus stageCategory;

	private String stageLabel;

	private String note;

	@Enumerated(EnumType.STRING)
	private RejectionReason rejectionReason;

	@Enumerated(EnumType.STRING)
	private @Nullable InterviewFormat interviewFormat;

	private @Nullable String interviewLocation;

	private @Nullable Integer durationMinutes;

	@CreationTimestamp
	private Instant createdAt;

	// Only ApplicationEntity.addEvent creates events, so the application's status always follows its timeline
	TimelineEventEntity(ApplicationEntity application, NewTimelineEvent event) {
		this.application = application;
		this.eventDate = event.eventDate();
		this.eventTime = event.eventTime();
		this.stageCategory = event.stageCategory();
		this.stageLabel = event.stageLabel();
		this.note = event.note();
		this.rejectionReason = event.rejectionReason();
		this.interviewFormat = event.interviewFormat();
		this.interviewLocation = event.interviewLocation();
		this.durationMinutes = event.durationMinutes();
	}

	/**
	 * Whether this event happened after the given moment. Within a day, an event without a time counts as the start
	 * of the day; two events at the same moment are equal, neither is after the other.
	 */
	boolean isAfter(LocalDate date, @Nullable LocalTime time) {
		int byDate = this.eventDate.compareTo(date);
		if (byDate != 0) {
			return byDate > 0;
		}
		return this.eventTime != null && (time == null || this.eventTime.isAfter(time));
	}

}
