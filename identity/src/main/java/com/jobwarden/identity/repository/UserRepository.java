package com.jobwarden.identity.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.jobwarden.identity.entity.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, long id);

	@Query("select u.id from UserEntity u where u.passwordHash is not null")
	Set<Long> findIdsWithPassword();

	Optional<UserEntity> findByPublicId(UUID publicId);

}
