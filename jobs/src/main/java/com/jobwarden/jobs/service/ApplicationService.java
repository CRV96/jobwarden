package com.jobwarden.jobs.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's data.
 */
public interface ApplicationService {

	/**
	 * Starts tracking a job posting. Its timeline starts with an event for the initial status, at the application's
	 * {@code initialStatusDate} and {@code initialStatusTime}.
	 *
	 * @return the new application's id
	 */
	long create(NewApplication application);

	/**
	 * Starts tracking a job posting, unless the profile already tracks the same link. Then nothing is duplicated:
	 * the existing application is returned, and if it's still {@link ApplicationStatus#SAVED} it moves to the
	 * posting's initial status. So a bookmarked posting you've since applied to becomes {@code APPLIED}; an application
	 * that's further along never moves back. Without a link, it's always a new application.
	 */
	TrackedApplication track(NewApplication application);

	/**
	 * @param status only return applications with this status, or all of them when {@code null}
	 */
	List<ApplicationSummary> findAll(long userId, @Nullable ApplicationStatus status);

	Optional<ApplicationDetails> findById(long userId, long applicationId);

	/**
	 * The application tracking this link, if the profile has one. The extension looks up the page it's on with it.
	 */
	Optional<ApplicationDetails> findByLink(long userId, String link);

	/**
	 * Adds an event to the timeline. The application's status follows its latest event.
	 */
	void addEvent(long userId, long applicationId, NewTimelineEvent event);

	/**
	 * Takes back "I applied", for an application that got no further: it goes back to
	 * {@link ApplicationStatus#SAVED}, as if it had only been bookmarked, and its APPLIED events leave the timeline.
	 */
	void moveBackToSaved(long userId, long applicationId);

	void delete(long userId, long applicationId);

}
