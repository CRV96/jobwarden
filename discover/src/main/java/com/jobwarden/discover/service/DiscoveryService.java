package com.jobwarden.discover.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.search.SearchCriteria;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's data.
 */
public interface DiscoveryService {

	/**
	 * @return the profile's saved searches, by name
	 */
	List<SavedSearch> findSearches(long userId);

	SavedSearch findSearch(long userId, long searchId);

	/**
	 * Keeps the filters under the name; a search with the name already, ignoring case, gets them instead.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the name is blank
	 */
	SavedSearch saveSearch(long userId, String name, SearchCriteria criteria);

	/**
	 * Deletes the search. The jobs found for it stay, without it.
	 */
	void deleteSearch(long userId, long searchId);

	/**
	 * Adds jobs to review. A link the profile already has, as a found job or as an application, is left out, so a job
	 * isn't suggested twice, also once it was discarded.
	 */
	AddedFoundJobs addFoundJobs(long userId, List<NewFoundJob> jobs);

	/**
	 * @param searchId only the jobs found for this saved search; all of them when {@code null}
	 * @return the job to review next: the one found first
	 */
	Optional<FoundJob> nextToReview(long userId, @Nullable Long searchId);

	/**
	 * @param searchId only the jobs found for this saved search; all of them when {@code null}
	 */
	long countToReview(long userId, @Nullable Long searchId);

	/**
	 * How many jobs wait for each search, and for none.
	 */
	List<SearchCount> countToReviewBySearch(long userId);

	/**
	 * @param decision only the jobs with this decision, latest decided first; all of them, latest found first, when
	 * {@code null}
	 */
	List<FoundJob> findFoundJobs(long userId, @Nullable Decision decision, int limit);

	/**
	 * @return the jobs saved, applied to or discarded, the latest decided first
	 */
	List<FoundJob> findDecided(long userId, int limit);

	/**
	 * Saving or applying makes the job an application: Saved or Applied, today. Discarding sets it aside, and
	 * {@link Decision#PENDING} brings back a discarded one to review. A job already saved or applied to is an
	 * application: it can't change any more, so it's returned as it is.
	 */
	FoundJob decide(long userId, long foundJobId, Decision decision);

}
