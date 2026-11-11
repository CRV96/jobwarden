package com.jobwarden.cv.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.OutreachDraftEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraftEntity, Long> {

	List<OutreachDraftEntity> findByUserIdAndApplicationIdOrderByIdAsc(long userId, long applicationId);

	Optional<OutreachDraftEntity> findByIdAndUserId(long id, long userId);

	/**
	 * @return the jobs among these that have messages, each with how many, as pairs of the job's id and the count
	 */
	@Query("""
			select d.applicationId, count(d)
			from OutreachDraftEntity d
			where d.userId = :userId and d.applicationId in :applicationIds
			group by d.applicationId""")
	List<Object[]> countByApplication(long userId, Collection<Long> applicationIds);

	@Modifying
	@Query("delete from OutreachDraftEntity d where d.userId = :userId and d.id = :id")
	int deleteOwned(long userId, long id);

}
