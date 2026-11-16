package com.jobwarden.jobs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.entity.JobOfferEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOfferEntity, Long> {

	Optional<JobOfferEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	List<JobOfferEntity> findByUserIdAndApplicationIdIn(long userId, Collection<Long> applicationIds);

}
