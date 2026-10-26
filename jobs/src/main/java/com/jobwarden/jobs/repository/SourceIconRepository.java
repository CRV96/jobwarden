package com.jobwarden.jobs.repository;

import java.util.Set;

import com.jobwarden.jobs.entity.SourceIconEntity;
import com.jobwarden.jobs.entity.SourceIconKey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SourceIconRepository extends JpaRepository<SourceIconEntity, SourceIconKey> {

	/**
	 * The sites the profile has an icon for, so a page knows which to show without loading the images.
	 */
	@Query("select i.key.source from SourceIconEntity i where i.key.userId = :userId")
	Set<String> findSources(long userId);

}
