package com.jobwarden.apply.repository;

import com.jobwarden.apply.entity.ApplicationAnswersEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAnswersRepository extends JpaRepository<ApplicationAnswersEntity, Long> {

}
