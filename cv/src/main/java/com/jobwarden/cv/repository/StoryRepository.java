package com.jobwarden.cv.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.StoryEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StoryRepository extends JpaRepository<StoryEntity, Long> {

	List<StoryEntity> findByUserIdOrderByIdAsc(long userId);

	List<StoryEntity> findByUserIdAndIdIn(long userId, Collection<Long> ids);

	Optional<StoryEntity> findByIdAndUserId(long id, long userId);

	long countByUserId(long userId);

	@Modifying
	@Query("delete from StoryEntity s where s.userId = :userId and s.id = :id")
	int deleteOwned(long userId, long id);

}
