package com.jobwarden.inbox.repository;

import com.jobwarden.inbox.entity.MailAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MailAccountRepository extends JpaRepository<MailAccountEntity, Long> {
}
