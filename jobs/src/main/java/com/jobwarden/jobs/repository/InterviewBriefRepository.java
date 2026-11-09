package com.jobwarden.jobs.repository;

import java.util.Optional;

import com.jobwarden.jobs.entity.InterviewBriefEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewBriefRepository extends JpaRepository<InterviewBriefEntity, Long> {

	Optional<InterviewBriefEntity> findByUserIdAndApplicationId(long userId, long applicationId);

}
