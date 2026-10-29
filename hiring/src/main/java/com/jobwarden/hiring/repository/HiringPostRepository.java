package com.jobwarden.hiring.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.hiring.entity.HiringPostEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HiringPostRepository extends JpaRepository<HiringPostEntity, Long> {

	/**
	 * With their authors, which every post shows: in the same query.
	 */
	@EntityGraph(attributePaths = "recruiter")
	List<HiringPostEntity> findByUserIdAndHiddenFalse(long userId);

	@EntityGraph(attributePaths = "recruiter")
	Optional<HiringPostEntity> findByIdAndUserId(long id, long userId);

	@Query("select p.link from HiringPostEntity p where p.userId = :userId")
	List<String> findLinks(long userId);

	/**
	 * @return each recruiter's id with how many of their posts aren't hidden
	 */
	@Query("""
			select p.recruiter.id, count(p) from HiringPostEntity p
			where p.userId = :userId and p.hidden = false
			group by p.recruiter.id""")
	List<Object[]> countByRecruiter(long userId);

}
