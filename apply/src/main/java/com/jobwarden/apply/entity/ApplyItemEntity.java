package com.jobwarden.apply.entity;

import java.time.Instant;

import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "apply_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplyItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	@Enumerated(EnumType.STRING)
	private ApplyMode mode;

	@Enumerated(EnumType.STRING)
	private ApplyStatus status = ApplyStatus.QUEUED;

	private @Nullable String site;

	private @Nullable String note;

	private @Nullable String question;

	@CreationTimestamp
	private Instant createdAt;

	private @Nullable Instant finishedAt;

	public ApplyItemEntity(long userId, long applicationId, ApplyMode mode) {
		this.userId = userId;
		this.applicationId = applicationId;
		this.mode = mode;
	}

	public void start() {
		this.status = ApplyStatus.APPLYING;
	}

	/**
	 * @param status where it ended, or {@link ApplyStatus#FILLED}, which waits for the profile
	 */
	public void finish(ApplyStatus status, @Nullable String site, @Nullable String note, @Nullable String question) {
		this.status = status;
		this.site = (site != null) ? site : this.site;
		this.note = (note != null) ? note : this.note;
		this.question = question;
		this.finishedAt = Instant.now();
	}

	/**
	 * Back in the queue, as it was asked, with what Claude said last time kept until it says more.
	 */
	public void requeue() {
		this.status = ApplyStatus.QUEUED;
		this.question = null;
		this.finishedAt = null;
	}

}
