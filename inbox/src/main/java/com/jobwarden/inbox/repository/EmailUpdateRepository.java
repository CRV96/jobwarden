package com.jobwarden.inbox.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.inbox.entity.EmailUpdateEntity;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailUpdateRepository extends JpaRepository<EmailUpdateEntity, Long> {

	/** The newest first. */
	List<EmailUpdateEntity> findByUserIdAndDecisionOrderByReceivedAtDescIdDesc(long userId, UpdateDecision decision);

	long countByUserIdAndDecision(long userId, UpdateDecision decision);

	boolean existsByUserIdAndMessageId(long userId, String messageId);

	Optional<EmailUpdateEntity> findByIdAndUserIdAndDecision(long id, long userId, UpdateDecision decision);

	/** Those decided by one of them since then, the latest first. A query of its own: "By" and "After" read as words. */
	@Query("""
			SELECT u FROM EmailUpdateEntity u
			WHERE u.userId = :userId AND u.decidedBy IN :deciders AND u.decidedAt > :since
			ORDER BY u.decidedAt DESC, u.id DESC
			""")
	List<EmailUpdateEntity> findByUserIdAndDecidedByInAndDecidedAtAfterOrderByDecidedAtDescIdDesc(long userId,
			Collection<UpdateDecider> deciders, Instant since);

}
