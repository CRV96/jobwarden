package com.jobwarden.cv.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.CoverLetterEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CoverLetterRepository extends JpaRepository<CoverLetterEntity, Long> {

	Optional<CoverLetterEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	/**
	 * @return which of the jobs have a cover letter
	 */
	@Query("""
			select c.applicationId
			from CoverLetterEntity c
			where c.userId = :userId and c.applicationId in :applicationIds""")
	List<Long> findApplicationIdsWithLetter(long userId, Collection<Long> applicationIds);

	@Modifying
	@Query("delete from CoverLetterEntity c where c.userId = :userId and c.applicationId = :applicationId")
	int deleteOwned(long userId, long applicationId);

}
