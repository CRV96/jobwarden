package com.jobwarden.discover.service.impl;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.AddedFoundJobs.SkippedLink;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.entity.AiRequestEntity;
import com.jobwarden.discover.entity.FoundJobEntity;
import com.jobwarden.discover.entity.SavedSearchEntity;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.exception.AiRequestNotFoundException;
import com.jobwarden.discover.exception.DiscoverErrorCode;
import com.jobwarden.discover.exception.FoundJobNotFoundException;
import com.jobwarden.discover.exception.SavedSearchNotFoundException;
import com.jobwarden.discover.repository.AiRequestRepository;
import com.jobwarden.discover.repository.FoundJobRepository;
import com.jobwarden.discover.repository.SavedSearchRepository;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.SearchCriteria;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CustomLog
@RequiredArgsConstructor
class DefaultDiscoveryService implements DiscoveryService {

	private final SavedSearchRepository searches;

	private final FoundJobRepository foundJobs;

	private final AiRequestRepository requests;

	private final ApplicationService applications;

	@Override
	@Transactional(readOnly = true)
	public List<SavedSearch> findSearches(long userId) {
		return this.searches.findByUserIdOrderByNameAsc(userId).stream().map(DefaultDiscoveryService::toSavedSearch).toList();
	}

	/**
	 * @throws SavedSearchNotFoundException if the user has no such search
	 */
	@Override
	@Transactional(readOnly = true)
	public SavedSearch findSearch(long userId, long searchId) {
		return toSavedSearch(getOwnedSearch(userId, searchId));
	}

	@Override
	@Transactional
	public SavedSearch saveSearch(long userId, String name, SearchCriteria criteria) {
		if (name == null || name.isBlank()) {
			throw new BusinessRuleException(DiscoverErrorCode.SEARCH_NAME_REQUIRED, "A saved search needs a name");
		}
		String trimmed = name.strip();
		Optional<SavedSearchEntity> existing = this.searches.findByUserIdAndNameIgnoreCase(userId, trimmed);
		SavedSearchEntity search = existing.orElseGet(() -> new SavedSearchEntity(userId, trimmed, criteria));
		if (existing.isPresent()) {
			search.change(trimmed, criteria);
		}
		search = this.searches.save(search);
		log.info("{} saved search {} for profile {}", existing.isPresent() ? "Changed" : "Created", search.getId(), userId);
		return toSavedSearch(search);
	}

	/**
	 * @throws SavedSearchNotFoundException if the user has no such search
	 */
	@Override
	@Transactional
	public void deleteSearch(long userId, long searchId) {
		this.searches.delete(getOwnedSearch(userId, searchId));
		log.info("Deleted saved search {} of profile {}", searchId, userId);
	}

	/**
	 * @throws SavedSearchNotFoundException if a job names a search the user doesn't have
	 */
	@Override
	@Transactional
	public AddedFoundJobs addFoundJobs(long userId, List<NewFoundJob> jobs) {
		List<FoundJobEntity> added = new ArrayList<>();
		List<SkippedLink> skipped = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (NewFoundJob job : jobs) {
			if (job.searchId() != null) {
				getOwnedSearch(userId, job.searchId());
			}
			if (job.requestId() != null) {
				getOwnedRequest(userId, job.requestId());
			}
			String link = tidyLink(job.link());
			if (link == null) {
				skipped.add(new SkippedLink(job.link(), "not a web address"));
			} else if (!seen.add(link) || this.foundJobs.existsByUserIdAndLink(userId, link)) {
				skipped.add(new SkippedLink(link, "already found"));
			} else if (this.applications.findByLink(userId, link).isPresent()) {
				skipped.add(new SkippedLink(link, "already an application"));
			} else {
				added.add(this.foundJobs.save(new FoundJobEntity(userId, job, link, sourceOf(link))));
			}
		}
		log.info("Added {} found jobs for profile {}, skipped {}", added.size(), userId, skipped.size());
		return new AddedFoundJobs(toFoundJobs(userId, added), skipped);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FoundJob> nextToReview(long userId, @Nullable Long searchId) {
		Optional<FoundJobEntity> next = (searchId == null)
				? this.foundJobs.findFirstByUserIdAndDecisionOrderByFoundAtAscIdAsc(userId, Decision.PENDING)
				: this.foundJobs.findFirstByUserIdAndDecisionAndSavedSearchIdOrderByFoundAtAscIdAsc(userId,
						Decision.PENDING, searchId);
		return next.map(job -> toFoundJobs(userId, List.of(job)).getFirst());
	}

	@Override
	@Transactional(readOnly = true)
	public long countToReview(long userId, @Nullable Long searchId) {
		return (searchId == null) ? this.foundJobs.countByUserIdAndDecision(userId, Decision.PENDING)
				: this.foundJobs.countByUserIdAndDecisionAndSavedSearchId(userId, Decision.PENDING, searchId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SearchCount> countToReviewBySearch(long userId) {
		return this.foundJobs.countBySearch(userId, Decision.PENDING);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FoundJob> findFoundJobs(long userId, @Nullable Decision decision, int limit) {
		List<FoundJobEntity> found = (decision == null)
				? this.foundJobs.findByUserIdOrderByFoundAtDescIdDesc(userId, Limit.of(limit))
				: this.foundJobs.findByUserIdAndDecisionOrderByDecidedAtDescIdDesc(userId, decision, Limit.of(limit));
		return toFoundJobs(userId, found);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FoundJob> findDecided(long userId, int limit) {
		return toFoundJobs(userId,
				this.foundJobs.findByUserIdAndDecisionNotOrderByDecidedAtDescIdDesc(userId, Decision.PENDING, Limit.of(limit)));
	}

	/**
	 * @throws FoundJobNotFoundException if the user has no such found job
	 */
	@Override
	@Transactional
	public FoundJob decide(long userId, long foundJobId, Decision decision) {
		FoundJobEntity job = this.foundJobs.findByIdAndUserId(foundJobId, userId)
			.orElseThrow(() -> new FoundJobNotFoundException(foundJobId));
		boolean isApplication = job.getDecision() == Decision.SAVED || job.getDecision() == Decision.APPLIED;
		if (!isApplication && job.getDecision() != decision) {
			Long applicationId = switch (decision) {
				case SAVED -> track(job, ApplicationStatus.SAVED);
				case APPLIED -> track(job, ApplicationStatus.APPLIED);
				case PENDING, DISCARDED -> null;
			};
			job.decide(decision, applicationId);
			log.info("Found job {} of profile {} is now {}", foundJobId, userId, decision);
		}
		return toFoundJobs(userId, List.of(job)).getFirst();
	}

	@Override
	@Transactional
	public AiRequest createRequest(long userId, AiRequestKind kind, AiRequirements requirements) {
		if (kind.isAboutAJob()) {
			throw new IllegalArgumentException(kind + " is about one job, which the request must name");
		}
		AiRequestEntity request = this.requests.save(new AiRequestEntity(userId, kind, requirements, null));
		log.info("Profile {} asked Claude for {} (request {})", userId, kind, request.getId());
		return toAiRequests(userId, List.of(request)).getFirst();
	}

	/**
	 * @throws ApplicationNotFoundException if the profile has no such job
	 */
	@Override
	@Transactional
	public AiRequest createRequest(long userId, AiRequestKind kind, long applicationId) {
		if (!kind.isAboutAJob()) {
			throw new IllegalArgumentException(kind + " isn't about one job");
		}
		if (this.applications.findById(userId, applicationId).isEmpty()) {
			throw new ApplicationNotFoundException(applicationId);
		}
		AiRequestEntity request = this.requests.save(new AiRequestEntity(userId, kind, AiRequirements.NONE, applicationId));
		log.info("Profile {} asked Claude for {} about application {} (request {})", userId, kind, applicationId,
				request.getId());
		return toAiRequests(userId, List.of(request)).getFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AiRequest> findLatestRequest(long userId, AiRequestKind kind, long applicationId) {
		return this.requests.findFirstByUserIdAndKindAndApplicationIdOrderByCreatedAtDescIdDesc(userId, kind, applicationId)
			.map(request -> toAiRequests(userId, List.of(request)).getFirst());
	}

	@Override
	@Transactional(readOnly = true)
	public List<AiRequest> findRequests(long userId, int limit) {
		return toAiRequests(userId, this.requests.findByUserIdOrderByCreatedAtDescIdDesc(userId, Limit.of(limit)));
	}

	/**
	 * @throws AiRequestNotFoundException if the profile has no such request
	 */
	@Override
	@Transactional(readOnly = true)
	public AiRequest findRequest(long userId, long requestId) {
		return toAiRequests(userId, List.of(getOwnedRequest(userId, requestId))).getFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public List<AiRequest> findOpenRequests(long userId) {
		return toAiRequests(userId, this.requests.findByUserIdAndStatusInOrderByCreatedAtAscIdAsc(userId,
				EnumSet.of(AiRequestStatus.WAITING, AiRequestStatus.WORKING)));
	}

	/**
	 * @throws AiRequestNotFoundException if the profile has no such request
	 * @throws BusinessRuleException if it's already finished
	 */
	@Override
	@Transactional
	public AiRequest startRequest(long userId, long requestId) {
		AiRequestEntity request = getOpenRequest(userId, requestId);
		if (request.getStatus() == AiRequestStatus.WAITING) {
			request.start();
			log.info("Claude started request {} of profile {}", requestId, userId);
		}
		return toAiRequests(userId, List.of(request)).getFirst();
	}

	/**
	 * @throws AiRequestNotFoundException if the profile has no such request
	 * @throws BusinessRuleException if it's already finished
	 */
	@Override
	@Transactional
	public AiRequest finishRequest(long userId, long requestId, boolean done, @Nullable String summary) {
		AiRequestEntity request = getOpenRequest(userId, requestId);
		request.finish(done ? AiRequestStatus.DONE : AiRequestStatus.FAILED,
				(summary == null || summary.isBlank()) ? null : summary.strip());
		log.info("Claude finished request {} of profile {}: {}", requestId, userId, request.getStatus());
		return toAiRequests(userId, List.of(request)).getFirst();
	}

	/**
	 * @throws AiRequestNotFoundException if the profile has no such request
	 */
	@Override
	@Transactional
	public AiRequest cancelRequest(long userId, long requestId) {
		AiRequestEntity request = getOwnedRequest(userId, requestId);
		if (request.getStatus().isOpen()) {
			request.finish(AiRequestStatus.CANCELLED, null);
			log.info("Profile {} cancelled request {}", userId, requestId);
		}
		return toAiRequests(userId, List.of(request)).getFirst();
	}

	/**
	 * @throws AiRequestNotFoundException if the profile has no such request
	 */
	@Override
	@Transactional(readOnly = true)
	public List<FoundJob> findFoundJobsOfRequest(long userId, long requestId) {
		getOwnedRequest(userId, requestId);
		return toFoundJobs(userId, this.foundJobs.findByUserIdAndAiRequestIdOrderByIdAsc(userId, requestId));
	}

	private AiRequestEntity getOwnedRequest(long userId, long requestId) {
		return this.requests.findByIdAndUserId(requestId, userId).orElseThrow(() -> new AiRequestNotFoundException(requestId));
	}

	private AiRequestEntity getOpenRequest(long userId, long requestId) {
		AiRequestEntity request = getOwnedRequest(userId, requestId);
		if (!request.getStatus().isOpen()) {
			throw new BusinessRuleException(DiscoverErrorCode.AI_REQUEST_FINISHED,
					"Request " + requestId + " is already " + request.getStatus(), String.valueOf(requestId));
		}
		return request;
	}

	/** With how many jobs were found for each, counted once for all of them. */
	private List<AiRequest> toAiRequests(long userId, List<AiRequestEntity> entities) {
		Map<Long, Long> found = new HashMap<>();
		if (!entities.isEmpty()) {
			for (Object[] count : this.foundJobs.countByRequest(userId, entities.stream().map(AiRequestEntity::getId).toList())) {
				found.put((Long) count[0], (Long) count[1]);
			}
		}
		return entities.stream()
			.map(request -> AiRequest.builder()
				.id(request.getId())
				.kind(request.getKind())
				.requirements(request.toRequirements())
				.status(request.getStatus())
				.summary(request.getSummary())
				.foundJobs(found.getOrDefault(request.getId(), 0L))
				.createdAt(request.getCreatedAt())
				.startedAt(request.getStartedAt())
				.finishedAt(request.getFinishedAt())
				.applicationId(request.getApplicationId())
				.build())
			.toList();
	}

	/** Starts tracking the job, or finds the application already tracking its link. */
	private long track(FoundJobEntity job, ApplicationStatus status) {
		return this.applications.track(NewApplication.builder()
			.userId(job.getUserId())
			.title(job.getTitle())
			.company(job.getCompany())
			.description(job.getDescription())
			.link(job.getLink())
			.location(job.getLocation())
			.salary(job.getSalary())
			.initialStatus(status)
			.initialStatusDate(LocalDate.now())
			.build()).id();
	}

	private SavedSearchEntity getOwnedSearch(long userId, long searchId) {
		return this.searches.findByIdAndUserId(searchId, userId).orElseThrow(() -> new SavedSearchNotFoundException(searchId));
	}

	/**
	 * The link without its fragment, which never names another posting.
	 *
	 * @return {@code null} if it isn't an http or https address
	 */
	private static @Nullable String tidyLink(String link) {
		try {
			URI uri = URI.create(link.strip());
			if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					|| uri.getHost() == null) {
				return null;
			}
			return (uri.getRawFragment() == null) ? uri.toString()
					: uri.toString().substring(0, uri.toString().indexOf('#'));
		} catch (IllegalArgumentException notAnAddress) {
			return null;
		}
	}

	/** The site, as the jobs module names it: the host without www. */
	private static String sourceOf(String link) {
		String host = URI.create(link).getHost().toLowerCase(Locale.ROOT);
		return host.startsWith("www.") ? host.substring("www.".length()) : host;
	}

	private static SavedSearch toSavedSearch(SavedSearchEntity search) {
		return new SavedSearch(search.getId(), search.getName(), search.toCriteria(), search.getCreatedAt());
	}

	/** With the name of the search, and what the request, each was found for, looked up once for all of them. */
	private List<FoundJob> toFoundJobs(long userId, List<FoundJobEntity> jobs) {
		Map<Long, AiRequestKind> kinds = this.requests
			.findAllById(jobs.stream().map(FoundJobEntity::getAiRequestId).filter(id -> id != null).distinct().toList())
			.stream()
			.collect(Collectors.toMap(AiRequestEntity::getId, AiRequestEntity::getKind));
		Map<Long, String> names = jobs.stream().anyMatch(job -> job.getSavedSearchId() != null)
				? this.searches.findByUserIdOrderByNameAsc(userId)
					.stream()
					.collect(Collectors.toMap(SavedSearchEntity::getId, SavedSearchEntity::getName))
				: Map.of();
		Function<FoundJobEntity, String> searchName = job -> (job.getSavedSearchId() != null)
				? names.getOrDefault(job.getSavedSearchId(), job.getSearchLabel()) : job.getSearchLabel();
		return jobs.stream()
			.map(job -> FoundJob.builder()
				.id(job.getId())
				.title(job.getTitle())
				.company(job.getCompany())
				.location(job.getLocation())
				.salary(job.getSalary())
				.link(job.getLink())
				.source(job.getSource())
				.description(job.getDescription())
				.note(job.getNote())
				.postedOn(job.getPostedOn())
				.searchId(job.getSavedSearchId())
				.searchName(searchName.apply(job))
				.decision(job.getDecision())
				.foundAt(job.getFoundAt())
				.decidedAt(job.getDecidedAt())
				.applicationId(job.getApplicationId())
				.requestId(job.getAiRequestId())
				.requestKind((job.getAiRequestId() != null) ? kinds.get(job.getAiRequestId()) : null)
				.build())
			.toList();
	}

}
