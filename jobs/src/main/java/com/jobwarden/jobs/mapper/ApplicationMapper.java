package com.jobwarden.jobs.mapper;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.entity.ApplicationEntity;
import com.jobwarden.jobs.entity.TimelineEventEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts entities to the records the service returns. Entities never leave the module. (The list's rows skip
 * this: {@code ApplicationRepository.findSummaries} selects them straight into records.)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationMapper {

	/**
	 * Reads the application's events, so call it inside the service's transaction.
	 */
	public static ApplicationDetails toApplicationDetails(ApplicationEntity application) {
		return ApplicationDetails.builder()
			.id(application.getId())
			.title(application.getTitle())
			.company(application.getCompany())
			.description(application.getDescription())
			.link(application.getLink())
			.location(application.getLocation())
			.salary(application.getSalary())
			.employmentType(application.getEmploymentType())
			.source(application.getSource())
			.notes(application.getNotes())
			.status(application.getCurrentStatus())
			.capturedAt(application.getCapturedAt())
			.timeline(application.getEvents().stream().map(ApplicationMapper::toTimelineEvent).toList())
			.build();
	}

	public static TimelineEvent toTimelineEvent(TimelineEventEntity event) {
		return TimelineEvent.builder()
			.id(event.getId())
			.eventDate(event.getEventDate())
			.eventTime(event.getEventTime())
			.stageCategory(event.getStageCategory())
			.stageLabel(event.getStageLabel())
			.note(event.getNote())
			.rejectionReason(event.getRejectionReason())
			.build();
	}

}
