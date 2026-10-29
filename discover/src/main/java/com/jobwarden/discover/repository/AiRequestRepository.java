package com.jobwarden.discover.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.entity.AiRequestEntity;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequestEntity, Long> {

	Optional<AiRequestEntity> findByIdAndUserId(long id, long userId);

	List<AiRequestEntity> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Limit limit);

	Optional<AiRequestEntity> findFirstByUserIdAndKindAndApplicationIdOrderByCreatedAtDescIdDesc(long userId,
			AiRequestKind kind, long applicationId);

	/** Oldest first: Claude does them in the order they were asked. */
	List<AiRequestEntity> findByUserIdAndStatusInOrderByCreatedAtAscIdAsc(long userId, Collection<AiRequestStatus> statuses);

}
