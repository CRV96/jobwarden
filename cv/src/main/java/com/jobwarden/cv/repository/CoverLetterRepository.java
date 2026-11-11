package com.jobwarden.cv.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.CoverLetterEntity;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CoverLetterRepository extends JpaRepository<CoverLetterEntity, Long> {

	Optional<CoverLetterEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	/**
	 * The job's letter, locked till the transaction ends: whoever writes it next waits, and sees it as left.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CoverLetterEntity c where c.userId = :userId and c.applicationId = :applicationId")
	Optional<CoverLetterEntity> findForUpdate(long userId, long applicationId);

	List<CoverLetterEntity> findByUserIdAndApplicationIdIn(long userId, Collection<Long> applicationIds);

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
