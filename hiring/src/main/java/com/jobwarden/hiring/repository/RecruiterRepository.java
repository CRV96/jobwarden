package com.jobwarden.hiring.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.hiring.entity.RecruiterEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruiterRepository extends JpaRepository<RecruiterEntity, Long> {

	Optional<RecruiterEntity> findByIdAndUserId(long id, long userId);

	Optional<RecruiterEntity> findByUserIdAndProfileUrl(long userId, String profileUrl);

	List<RecruiterEntity> findByUserIdAndHiddenFalseOrderByNameAsc(long userId);

}
