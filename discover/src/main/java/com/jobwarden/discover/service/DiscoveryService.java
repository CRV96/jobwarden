package com.jobwarden.discover.service;

import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
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
	 *
	 * @throws com.jobwarden.discover.exception.AiRequestNotFoundException if a job names a request the profile doesn't
	 * have
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
	 * Asks Claude to do something: the request waits until Claude, working through the MCP server, picks it up.
	 */
	AiRequest createRequest(long userId, AiRequestKind kind, AiRequirements requirements);

	/**
	 * Asks Claude to do something about one job, e.g. tailor the CV to it.
	 *
	 * @throws IllegalArgumentException if the kind isn't about one job
	 * @throws com.jobwarden.jobs.exception.ApplicationNotFoundException if the profile has no such job
	 */
	AiRequest createRequest(long userId, AiRequestKind kind, long applicationId);

	/**
	 * @return the latest request of that kind about the job, finished or not
	 */
	Optional<AiRequest> findLatestRequest(long userId, AiRequestKind kind, long applicationId);

	/**
	 * @return the profile's requests, the latest first
	 */
	List<AiRequest> findRequests(long userId, int limit);

	/**
	 * @throws com.jobwarden.discover.exception.AiRequestNotFoundException if the profile has no such request
	 */
	AiRequest findRequest(long userId, long requestId);

	/**
	 * @return the requests waiting for Claude or being done, the oldest first
	 */
	List<AiRequest> findOpenRequests(long userId);

	/**
	 * Claude picked the request up: it's {@link AiRequestStatus#WORKING} from now on. One already working stays as it
	 * is.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's already finished
	 */
	AiRequest startRequest(long userId, long requestId);

	/**
	 * @param done whether Claude did it; else it failed
	 * @param summary what Claude did, or why it couldn't
	 * @throws com.jobwarden.common.exception.BusinessRuleException if it's already finished
	 */
	AiRequest finishRequest(long userId, long requestId, boolean done, @Nullable String summary);

	/**
	 * Takes a request back before it's finished. A finished one stays as it is.
	 */
	AiRequest cancelRequest(long userId, long requestId);

	/**
	 * @return the jobs Claude found for the request, in the order it added them
	 */
	List<FoundJob> findFoundJobsOfRequest(long userId, long requestId);

	/**
	 * Saving or applying makes the job an application: Saved or Applied, today. Discarding sets it aside, and
	 * {@link Decision#PENDING} brings back a discarded one to review. A job already saved or applied to is an
	 * application: it can't change any more, so it's returned as it is.
	 */
	FoundJob decide(long userId, long foundJobId, Decision decision);

}
