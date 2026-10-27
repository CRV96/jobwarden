package com.jobwarden.discover.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.entity.SavedSearchEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSearchRepository extends JpaRepository<SavedSearchEntity, Long> {

	List<SavedSearchEntity> findByUserIdOrderByNameAsc(long userId);

	Optional<SavedSearchEntity> findByIdAndUserId(long id, long userId);

	Optional<SavedSearchEntity> findByUserIdAndNameIgnoreCase(long userId, String name);

}
