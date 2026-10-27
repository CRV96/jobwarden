package com.jobwarden.jobs.repository;

import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.entity.CompanyLogoEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyLogoRepository extends JpaRepository<CompanyLogoEntity, Long> {

	/**
	 * The logo, if the application is the profile's.
	 */
	@Query("""
			select l from CompanyLogoEntity l
			where l.applicationId = :applicationId
			  and exists (select a from ApplicationEntity a where a.id = l.applicationId and a.userId = :userId)""")
	Optional<CompanyLogoEntity> findOwned(long userId, long applicationId);

	/**
	 * The profile's applications that have a logo, so a page knows which to show without loading the images.
	 */
	@Query("""
			select l.applicationId from CompanyLogoEntity l
			where exists (select a from ApplicationEntity a where a.id = l.applicationId and a.userId = :userId)""")
	Set<Long> findApplicationIds(long userId);

}
