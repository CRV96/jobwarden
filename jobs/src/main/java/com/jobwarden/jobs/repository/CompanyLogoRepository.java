package com.jobwarden.jobs.repository;

import java.util.List;
import java.util.Optional;

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
	 * The profile's applications that have a logo, with when each last changed, so a page knows which logos to show,
	 * and their versions, without loading the images.
	 */
	@Query("""
			select new com.jobwarden.jobs.repository.LogoVersion(l.applicationId, l.updatedAt) from CompanyLogoEntity l
			where exists (select a from ApplicationEntity a where a.id = l.applicationId and a.userId = :userId)""")
	List<LogoVersion> findVersions(long userId);

}
