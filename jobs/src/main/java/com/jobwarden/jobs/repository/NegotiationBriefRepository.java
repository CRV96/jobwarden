package com.jobwarden.jobs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.entity.NegotiationBriefEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NegotiationBriefRepository extends JpaRepository<NegotiationBriefEntity, Long> {

	Optional<NegotiationBriefEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	/**
	 * @return which of the jobs have a brief
	 */
	@Query("""
			select b.applicationId
			from NegotiationBriefEntity b
			where b.userId = :userId and b.applicationId in :applicationIds""")
	List<Long> findApplicationIdsWithBrief(long userId, Collection<Long> applicationIds);

}
