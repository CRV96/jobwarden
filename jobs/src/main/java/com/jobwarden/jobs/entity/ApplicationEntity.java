package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLOrder;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ApplicationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// A plain id, not a relation: modules reference each other's data by id, never by entity.
	private long userId;

	private String title;

	private String company;

	private String description;

	private String link;

	private String location;

	private String salary;

	private String employmentType;

	private String source;

	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayload;

	private @Nullable String notes;

	// Denormalized from the latest timeline event, so the list can filter without joining.
	@Enumerated(EnumType.STRING)
	private ApplicationStatus currentStatus;

	private Instant capturedAt;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	@OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
	// In SQL, because within a day the events without a time come first, like in TimelineEventEntity.isAfter
	@SQLOrder("event_date ASC, event_time ASC NULLS FIRST, id ASC")
	@Getter(AccessLevel.NONE)
	private List<TimelineEventEntity> events = new ArrayList<>();

	public ApplicationEntity(NewApplication application, @Nullable String source, Instant capturedAt) {
		this.userId = application.userId();
		this.title = application.title();
		this.company = application.company();
		this.description = application.description();
		this.link = application.link();
		this.location = application.location();
		this.salary = application.salary();
		this.employmentType = application.employmentType();
		this.source = source;
		this.rawPayload = application.rawPayload();
		this.currentStatus = application.initialStatus();
		this.capturedAt = capturedAt;
	}

	/**
	 * The timeline, oldest first. Read-only: events are added with {@link #addEvent} and removed with
	 * {@link #removeEvent}, which keep the status in step.
	 */
	public List<TimelineEventEntity> getEvents() {
		return Collections.unmodifiableList(this.events);
	}

	/**
	 * Adds the event to the timeline. The status follows the latest event, so it changes unless the new event
	 * happened after one already on the timeline. At the same moment, the event added last counts as the latest.
	 */
	public void addEvent(NewTimelineEvent event) {
		boolean latest = this.events.stream()
			.noneMatch(existing -> existing.isAfter(event.eventDate(), event.eventTime()));
		this.events.add(new TimelineEventEntity(this, event));
		if (latest) {
			this.currentStatus = event.stageCategory();
		}
		markModified();
	}

	/**
	 * Whether an event can leave the timeline: the timeline keeps at least one, the event its status comes from.
	 */
	public boolean canRemoveEvent() {
		return this.events.size() > 1;
	}

	/**
	 * Removes the event from the timeline. The status follows the latest event left, so removing the latest one
	 * takes the application back to the status before it. Only when {@link #canRemoveEvent()}.
	 *
	 * @return whether the timeline had the event
	 */
	public boolean removeEvent(long eventId) {
		boolean removed = this.events.removeIf(event -> Objects.equals(event.getId(), eventId));
		if (removed) {
			this.currentStatus = latestEvent().getStageCategory();
			markModified();
		}
		return removed;
	}

	/**
	 * @param notes free text about the application as a whole; {@code null} to clear them
	 */
	public void changeNotes(@Nullable String notes) {
		this.notes = notes;
	}

	public void changeSalary(@Nullable String salary) {
		this.salary = salary;
	}

	/**
	 * Whether "I applied" can be taken back: the application is APPLIED, and its timeline has nothing but the SAVED
	 * and APPLIED events that bookmarking and applying add. Once something else happened, the timeline tells more
	 * than a status change can undo.
	 */
	public boolean canMoveBackToSaved() {
		return this.currentStatus == ApplicationStatus.APPLIED && this.events.stream()
			.allMatch(event -> event.getStageCategory() == ApplicationStatus.SAVED
					|| event.getStageCategory() == ApplicationStatus.APPLIED);
	}

	/**
	 * Takes back "I applied": removes the APPLIED events, so the application is SAVED again, as if it had only been
	 * bookmarked. One saved as applied straight away has no SAVED event to go back to, so it gets one, at the moment
	 * of its first APPLIED event. Only when {@link #canMoveBackToSaved()}.
	 */
	public void moveBackToSaved() {
		TimelineEventEntity firstApplied = this.events.stream()
			.filter(event -> event.getStageCategory() == ApplicationStatus.APPLIED)
			.findFirst()
			.orElseThrow();
		this.events.removeIf(event -> event.getStageCategory() == ApplicationStatus.APPLIED);
		markModified();
		if (this.events.isEmpty()) {
			this.events.add(new TimelineEventEntity(this, NewTimelineEvent.builder()
				.eventDate(firstApplied.getEventDate())
				.eventTime(firstApplied.getEventTime())
				.stageCategory(ApplicationStatus.SAVED)
				.build()));
		}
		this.currentStatus = ApplicationStatus.SAVED;
	}

	/**
	 * The event the status comes from. The events are in timeline order, except those added since they were loaded,
	 * which are at the end; at the same moment, the one further down counts as the latest, like in {@link #addEvent}.
	 */
	/**
	 * A change to the timeline is a change to the application, even when its own columns stay the same, e.g. an
	 * interview that keeps it INTERVIEWING: the list's "last modified" counts it. Setting the time makes the row dirty,
	 * so {@code @UpdateTimestamp} then sets it again as Hibernate saves it.
	 */
	private void markModified() {
		this.updatedAt = Instant.now();
	}

	private TimelineEventEntity latestEvent() {
		return this.events.stream()
			.reduce((latest, event) -> latest.isAfter(event.getEventDate(), event.getEventTime()) ? latest : event)
			.orElseThrow();
	}

}
