package com.jobwarden.cv.repository;

import com.jobwarden.cv.entity.GitHubAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubAccountRepository extends JpaRepository<GitHubAccountEntity, Long> {
}
