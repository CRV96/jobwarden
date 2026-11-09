package com.jobwarden.jobs.repository;

import java.util.Optional;

import com.jobwarden.jobs.entity.CalendarFeedEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeedEntity, Long> {

	Optional<CalendarFeedEntity> findByToken(String token);

}
