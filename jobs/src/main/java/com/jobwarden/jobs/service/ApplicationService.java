package com.jobwarden.jobs.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.JobContact;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.dto.TidyResult;
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
	 * How many of the applications in use matching the search have each status, for every status, in the order of
	 * {@link ApplicationStatus}.
	 *
	 * @param search as in {@link ApplicationQuery#search()}
	 */
	default Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search) {
		return countByStatus(userId, search, false);
	}

	/**
	 * Like {@link #countByStatus(long, String)}, of the archived applications when asked.
	 */
	Map<ApplicationStatus, Long> countByStatus(long userId, @Nullable String search, boolean archived);

	/**
	 * How many applications the profile has in use: the archived ones left out.
	 */
	long count(long userId);

	/**
	 * How many applications the profile archived.
	 */
	long countArchived(long userId);

	/**
	 * Archives those of the applications the profile owns: they leave the list, the pipeline, the calendar and its
	 * feed, and the offers, and stay in the statistics and the salaries. Nothing else about them changes, so they come
	 * back as they were.
	 *
	 * @return how many it archived; those archived already, and another profile's, are left alone
	 */
	int archive(long userId, Collection<Long> applicationIds);

	/**
	 * Brings those of the archived applications the profile owns back into use.
	 *
	 * @return how many came back
	 */
	int restore(long userId, Collection<Long> applicationIds);

	/**
	 * Archives the applications in use that nothing changed on, their timeline included, since the start of that day.
	 *
	 * @param endedOnly only those that ended: rejected, with no response, closed or withdrawn
	 * @return how many it archived
	 */
	int archiveUnchangedSince(long userId, LocalDate day, boolean endedOnly);

	Optional<ApplicationDetails> findById(long userId, long applicationId);

	/**
	 * What happens between the two days, both included: the events of the applications' timelines, their deadlines and
	 * the days to follow up on them. By day, and within a day the entries without a time first, like on a timeline.
	 */
	List<CalendarEntry> findCalendar(long userId, LocalDate from, LocalDate to);

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
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the event has a rejection reason without being a
	 * rejection, or interview details without being an interview; or if it would say again what the event before it
	 * says ({@code STATUS_REPEATED}): a status that {@link ApplicationStatus#canRepeat() can't repeat}, right after the
	 * same one
	 */
	void addEvent(long userId, long applicationId, NewTimelineEvent event);

	/**
	 * Whether {@link #addEvent} would refuse the event as saying again what the event before it says, so a caller in a
	 * transaction of its own can leave it out rather than fail.
	 *
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such application
	 */
	boolean wouldRepeat(long userId, long applicationId, NewTimelineEvent event);

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
	 * @param salary as the posting gives it, e.g. "5,000 EUR a month"; {@code null} to clear it
	 */
	void updateSalary(long userId, long applicationId, @Nullable String salary);

	/**
	 * @param location where the job is, e.g. "Bucharest, hybrid"; {@code null} to clear it
	 */
	void updateLocation(long userId, long applicationId, @Nullable String location);

	/**
	 * @param followUp who to follow up with and when, replacing what was known; {@link FollowUp#NONE} to clear it
	 */
	void updateFollowUp(long userId, long applicationId, FollowUp followUp);

	/**
	 * @return the people saved as the contact of the profile's jobs, with a name or a profile link, the jobs added last
	 * first
	 */
	List<JobContact> findContacts(long userId);

	/**
	 * Tidies up the applications that got no answer: one at Applied whose timeline has had nothing for that many days
	 * moves to No response; a newer one without a day to follow up gets one, that many days after its latest event, or
	 * tomorrow when that's past.
	 *
	 * @param note for the timeline of those moved to No response, e.g. that the company never answered; none if
	 * {@code null}
	 */
	TidyResult tidyUnanswered(long userId, int noResponseAfterDays, int followUpAfterDays, @Nullable String note);

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
