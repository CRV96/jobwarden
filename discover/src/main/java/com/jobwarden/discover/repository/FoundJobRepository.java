package com.jobwarden.discover.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.entity.FoundJobEntity;
import com.jobwarden.discover.enums.Decision;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FoundJobRepository extends JpaRepository<FoundJobEntity, Long> {

	Optional<FoundJobEntity> findByIdAndUserId(long id, long userId);

	boolean existsByUserIdAndLink(long userId, String link);

	/** The queue to review, oldest first: what was found first is decided first. */
	Optional<FoundJobEntity> findFirstByUserIdAndDecisionOrderByFoundAtAscIdAsc(long userId, Decision decision);

	Optional<FoundJobEntity> findFirstByUserIdAndDecisionAndSavedSearchIdOrderByFoundAtAscIdAsc(long userId,
			Decision decision, long savedSearchId);

	long countByUserIdAndDecision(long userId, Decision decision);

	long countByUserIdAndDecisionAndSavedSearchId(long userId, Decision decision, long savedSearchId);

	@Query("""
			select new com.jobwarden.discover.dto.SearchCount(f.savedSearchId, count(f))
			from FoundJobEntity f
			where f.userId = :userId and f.decision = :decision
			group by f.savedSearchId""")
	List<SearchCount> countBySearch(long userId, Decision decision);

	List<FoundJobEntity> findByUserIdAndDecisionOrderByDecidedAtDescIdDesc(long userId, Decision decision, Limit limit);

	List<FoundJobEntity> findByUserIdOrderByFoundAtDescIdDesc(long userId, Limit limit);

	/** The jobs found for a request, in the order Claude added them. */
	List<FoundJobEntity> findByUserIdAndAiRequestIdOrderByIdAsc(long userId, long aiRequestId);

	@Query("""
			select f.aiRequestId, count(f)
			from FoundJobEntity f
			where f.userId = :userId and f.aiRequestId in :requestIds
			group by f.aiRequestId""")
	List<Object[]> countByRequest(long userId, Collection<Long> requestIds);

	List<FoundJobEntity> findByUserIdAndDecisionNotOrderByDecidedAtDescIdDesc(long userId, Decision decision, Limit limit);

}
