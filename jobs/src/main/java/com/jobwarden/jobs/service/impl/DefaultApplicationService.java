package com.jobwarden.jobs.service.impl;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.exception.TimelineEventNotFoundException;
import com.jobwarden.jobs.mapper.ApplicationMapper;
import com.jobwarden.jobs.repository.ApplicationRepository;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@CustomLog
@RequiredArgsConstructor
class DefaultApplicationService implements ApplicationService {

	private static final String WWW_PREFIX = "www.";

	private final ApplicationRepository applications;

	@Override
	@Transactional
	public long create(NewApplication application) {
		ApplicationEntity entity = new ApplicationEntity(application, sourceOf(application.link()), Instant.now());
		// Every timeline starts with the initial status, so it always has a first entry
		entity.addEvent(initialStatusEvent(application));
		long id = this.applications.save(entity).getId();
		log.info("Created application {} for profile {} as {}", id, application.userId(), application.initialStatus());
		return id;
	}

	@Override
	@Transactional
	public TrackedApplication track(NewApplication application) {
		String link = application.link();
		Optional<ApplicationEntity> existing = (link != null)
				? this.applications.findFirstByUserIdAndLink(application.userId(), link) : Optional.empty();

		if (existing.isEmpty()) {
			log.debug("No existing application for profile {} and link {}", application.userId(), link);
			return new TrackedApplication(create(application), true);
		}

		ApplicationEntity tracked = existing.get();
		log.debug("Link already tracked as application {}", tracked.getId());

		if (tracked.getCurrentStatus() == ApplicationStatus.SAVED
				&& application.initialStatus() != ApplicationStatus.SAVED) {
			tracked.addEvent(initialStatusEvent(application));

			log.info("Application {} captured again, moved from SAVED to {}", tracked.getId(),
					tracked.getCurrentStatus());
		}

		return new TrackedApplication(tracked.getId(), false);
	}

	@Override
	public List<ApplicationSummary> findAll(long userId, @Nullable ApplicationStatus status) {
		List<ApplicationSummary> found = (status != null) ? this.applications.findSummaries(userId, status)
				: this.applications.findSummaries(userId);
		log.debug("Found {} applications for profile {} (status: {})", found.size(), userId,
				(status != null) ? status : "any");
		return found;
	}

	@Override
	public Page<ApplicationSummary> find(long userId, ApplicationQuery query) {
		Page<ApplicationSummary> found = this.applications.findSummaries(userId, query);
		log.debug("Found {} of {} applications for profile {} ({})", found.getNumberOfElements(),
				found.getTotalElements(), userId, query);
		return found;
	}

	@Override
	public Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search) {
		Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus status : ApplicationStatus.values()) {
			counts.put(status, 0L);
		}
		counts.putAll(this.applications.countByStatus(userId, search));
		return counts;
	}

	@Override
	public long count(long userId) {
		return this.applications.countByUserId(userId);
	}

	@Override
	public Optional<ApplicationDetails> findById(long userId, long applicationId) {
		Optional<ApplicationDetails> application = this.applications.findByIdAndUserId(applicationId, userId)
			.map(ApplicationMapper::toApplicationDetails);
		log.debug("Application {} for profile {} {}", applicationId, userId,
				application.isPresent() ? "found" : "not found");
		return application;
	}

	@Override
	public Optional<ApplicationDetails> findByLink(long userId, String link) {
		Optional<ApplicationDetails> application = this.applications.findFirstByUserIdAndLink(userId, link)
			.map(ApplicationMapper::toApplicationDetails);
		log.debug("Looked up a link for profile {}: {}", userId,
				application.map(found -> "application " + found.id()).orElse("not tracked"));
		return application;
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 * @throws BusinessRuleException if the event has a rejection reason but isn't a rejection
	 */
	@Override
	@Transactional
	public void addEvent(long userId, long applicationId, NewTimelineEvent event) {
		ApplicationEntity application = getOwnedApplication(userId, applicationId);
		requireRejectionReasonOnlyOnRejection(event);
		ApplicationStatus previousStatus = application.getCurrentStatus();
		application.addEvent(event);
		if (application.getCurrentStatus() != previousStatus) {
			log.info("Application {} moved from {} to {}", applicationId, previousStatus,
					application.getCurrentStatus());
		} else {
			log.debug("Added a {} event to application {}; its status stays {}", event.stageCategory(),
					applicationId, previousStatus);
		}
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 * @throws TimelineEventNotFoundException if its timeline has no such event
	 * @throws BusinessRuleException if the event is the only one on the timeline
	 */
	@Override
	@Transactional
	public void removeEvent(long userId, long applicationId, long eventId) {
		ApplicationEntity application = getOwnedApplication(userId, applicationId);
		if (!application.canRemoveEvent()) {
			throw new BusinessRuleException(JobsErrorCode.CANNOT_REMOVE_LAST_EVENT,
					"Application " + applicationId + " keeps its only timeline event");
		}
		ApplicationStatus previousStatus = application.getCurrentStatus();
		if (!application.removeEvent(eventId)) {
			throw new TimelineEventNotFoundException(applicationId, eventId);
		}
		if (application.getCurrentStatus() != previousStatus) {
			log.info("Removed event {} from application {}, which moved from {} to {}", eventId, applicationId,
					previousStatus, application.getCurrentStatus());
		} else {
			log.info("Removed event {} from application {}; its status stays {}", eventId, applicationId,
					previousStatus);
		}
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public void updateNotes(long userId, long applicationId, @Nullable String notes) {
		getOwnedApplication(userId, applicationId).changeNotes(notes);
		log.debug("Updated the notes of application {}", applicationId);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 * @throws BusinessRuleException if it isn't APPLIED, or its timeline has more than SAVED and APPLIED events
	 */
	@Override
	@Transactional
	public void moveBackToSaved(long userId, long applicationId) {
		ApplicationEntity application = getOwnedApplication(userId, applicationId);
		if (!application.canMoveBackToSaved()) {
			throw new BusinessRuleException(JobsErrorCode.CANNOT_MOVE_BACK_TO_SAVED, "Application " + applicationId
					+ " can't move back to SAVED: it's " + application.getCurrentStatus()
					+ ", or its timeline has more than SAVED and APPLIED events");
		}
		application.moveBackToSaved();
		log.info("Application {} moved back from APPLIED to SAVED", applicationId);
	}

	/**
	 * @throws ApplicationNotFoundException if the user has no such application
	 */
	@Override
	@Transactional
	public void delete(long userId, long applicationId) {
		this.applications.delete(getOwnedApplication(userId, applicationId));
		log.info("Deleted application {}", applicationId);
	}

	@Override
	@Transactional
	public int deleteAll(long userId, Collection<Long> applicationIds) {
		if (applicationIds.isEmpty()) {
			return 0;
		}
		int deleted = this.applications.deleteOwned(userId, applicationIds);
		log.info("Deleted {} of the {} applications asked for by profile {}", deleted, applicationIds.size(), userId);
		return deleted;
	}

	/**
	 * The site the posting came from, e.g. {@code linkedin.com}.
	 */
	private static @Nullable String sourceOf(@Nullable String link) {
		if (link == null) {
			return null;
		}
		try {
			String host = URI.create(link).getHost();
			return (host != null && host.startsWith(WWW_PREFIX)) ? host.substring(WWW_PREFIX.length()) : host;
		} catch (IllegalArgumentException exception) {
			// Not a link Java can parse. The source is optional, so the application is saved without one.
			return null;
		}
	}

	/**
	 * The event that records the posting's initial status, with no label or note.
	 */
	private static NewTimelineEvent initialStatusEvent(NewApplication application) {
		return NewTimelineEvent.builder()
			.eventDate(application.initialStatusDate())
			.eventTime(application.initialStatusTime())
			.stageCategory(application.initialStatus())
			.build();
	}

	private static void requireRejectionReasonOnlyOnRejection(NewTimelineEvent event) {
		if (event.rejectionReason() != null && event.stageCategory() != ApplicationStatus.REJECTED) {
			throw new BusinessRuleException(JobsErrorCode.REJECTION_REASON_NOT_ALLOWED,
					"A rejection reason is only allowed on a REJECTED event, not on " + event.stageCategory());
		}
	}

	private ApplicationEntity getOwnedApplication(long userId, long applicationId) {
		return this.applications.findByIdAndUserId(applicationId, userId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
