package com.jobwarden.jobs.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's data.
 */
public interface ApplicationService {

	/**
	 * Starts tracking a job posting. Its timeline starts with an event for the initial status, at the application's
	 * {@code initialStatusDate} and {@code initialStatusTime}. Its {@code sourceIcon}, if any, becomes the site's, and
	 * its {@code companyLogo} its own.
	 *
	 * @return the new application's id
	 */
	long create(NewApplication application);

	/**
	 * Starts tracking a job posting, unless the profile already tracks the same link. Then nothing is duplicated:
	 * the existing application is returned, and if it's still {@link ApplicationStatus#SAVED} it moves to the
	 * posting's initial status. So a bookmarked posting you've since applied to becomes {@code APPLIED}; an application
	 * that's further along never moves back. Without a link, it's always a new application. Either way, the posting's
	 * {@code sourceIcon}, if any, becomes the site's, and its {@code companyLogo} the application's.
	 */
	TrackedApplication track(NewApplication application);

	/**
	 * @param status only return applications with this status, or all of them when {@code null}
	 */
	List<ApplicationSummary> findAll(long userId, @Nullable ApplicationStatus status);

	/**
	 * One page of the application list, searched, filtered and sorted as the query asks.
	 */
	Page<ApplicationSummary> find(long userId, ApplicationQuery query);

	/**
	 * How many of the applications matching the search have each status, for every status, in the order of
	 * {@link ApplicationStatus}.
	 *
	 * @param search as in {@link ApplicationQuery#search()}
	 */
	Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search);

	/**
	 * How many applications the profile has.
	 */
	long count(long userId);

	Optional<ApplicationDetails> findById(long userId, long applicationId);

	/**
	 * The application tracking this link, if the profile has one. The extension looks up the page it's on with it.
	 */
	Optional<ApplicationDetails> findByLink(long userId, String link);

	/**
	 * The icon kept for a site the profile saved postings from.
	 *
	 * @param source the site, e.g. {@code linkedin.com}: an application's source
	 */
	Optional<PngImage> findSourceIcon(long userId, String source);

	/**
	 * The sites the profile has an icon for, so a page knows which icons to show without loading them.
	 */
	Set<String> findSourcesWithIcon(long userId);

	/**
	 * The company logo of one of the profile's applications.
	 */
	Optional<PngImage> findCompanyLogo(long userId, long applicationId);

	/**
	 * The profile's applications that have a company logo, so a page knows which logos to show without loading them.
	 */
	Set<Long> findApplicationsWithLogo(long userId);

	/**
	 * Adds an event to the timeline. The application's status follows its latest event.
	 */
	void addEvent(long userId, long applicationId, NewTimelineEvent event);

	/**
	 * Removes an event from the timeline, e.g. one added by mistake. The status follows the latest event left. The
	 * timeline keeps at least one event.
	 */
	void removeEvent(long userId, long applicationId, long eventId);

	/**
	 * @param notes free text about the application as a whole; {@code null} to clear them
	 */
	void updateNotes(long userId, long applicationId, @Nullable String notes);

	/**
	 * Takes back "I applied", for an application that got no further: it goes back to
	 * {@link ApplicationStatus#SAVED}, as if it had only been bookmarked, and its APPLIED events leave the timeline.
	 */
	void moveBackToSaved(long userId, long applicationId);

	void delete(long userId, long applicationId);

	/**
	 * Deletes those of the applications the profile owns, with their timelines. The others are left alone: they belong
	 * to another profile, or they're already gone, e.g. deleted in another tab.
	 *
	 * @return how many applications were deleted
	 */
	int deleteAll(long userId, Collection<Long> applicationIds);

}
