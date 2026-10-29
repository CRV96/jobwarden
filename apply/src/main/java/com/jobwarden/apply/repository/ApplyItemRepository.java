package com.jobwarden.apply.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.apply.entity.ApplyItemEntity;
import com.jobwarden.apply.enums.ApplyStatus;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplyItemRepository extends JpaRepository<ApplyItemEntity, Long> {

	Optional<ApplyItemEntity> findByIdAndUserId(long id, long userId);

	List<ApplyItemEntity> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Limit limit);

	/** Oldest first: Claude does them in the order they were queued. */
	List<ApplyItemEntity> findByUserIdAndStatusInOrderByCreatedAtAscIdAsc(long userId, Collection<ApplyStatus> statuses);

	boolean existsByUserIdAndApplicationIdAndStatusIn(long userId, long applicationId, Collection<ApplyStatus> statuses);

}
