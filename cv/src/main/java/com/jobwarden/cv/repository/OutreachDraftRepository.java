package com.jobwarden.cv.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.OutreachDraftEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraftEntity, Long> {

	List<OutreachDraftEntity> findByUserIdAndApplicationIdOrderByIdAsc(long userId, long applicationId);

	Optional<OutreachDraftEntity> findByIdAndUserId(long id, long userId);

	@Modifying
	@Query("delete from OutreachDraftEntity d where d.userId = :userId and d.id = :id")
	int deleteOwned(long userId, long id);

}
