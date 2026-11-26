package com.jobwarden.jobs.repository;

import java.util.Optional;

import com.jobwarden.jobs.entity.CompanyResearchEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyResearchRepository extends JpaRepository<CompanyResearchEntity, Long> {

	Optional<CompanyResearchEntity> findByUserIdAndApplicationId(long userId, long applicationId);

}
