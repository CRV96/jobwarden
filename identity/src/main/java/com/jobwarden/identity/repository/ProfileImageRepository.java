package com.jobwarden.identity.repository;

import com.jobwarden.identity.entity.ProfileImageEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The profiles' photos, by profile id.
 */
@Repository
public interface ProfileImageRepository extends JpaRepository<ProfileImageEntity, Long> {

}
