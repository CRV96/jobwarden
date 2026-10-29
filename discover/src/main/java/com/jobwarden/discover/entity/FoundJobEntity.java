package com.jobwarden.discover.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.enums.Decision;
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
@Table(name = "found_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoundJobEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private @Nullable Long savedSearchId;

	private @Nullable Long aiRequestId;

	private @Nullable String searchLabel;

	private String title;

	private @Nullable String company;

	private @Nullable String location;

	private @Nullable String salary;

	private String link;

	private @Nullable String source;

	private @Nullable String description;

	private @Nullable String note;

	private @Nullable LocalDate postedOn;

	@Enumerated(EnumType.STRING)
	private Decision decision = Decision.PENDING;

	private @Nullable Instant decidedAt;

	private @Nullable Long applicationId;

	@CreationTimestamp
	private Instant foundAt;

	/**
	 * @param link the job's link, already checked and tidied by the service
	 * @param source the site, taken from the link
	 */
	public FoundJobEntity(long userId, NewFoundJob job, String link, @Nullable String source) {
		this.userId = userId;
		this.savedSearchId = job.searchId();
		this.aiRequestId = job.requestId();
		this.searchLabel = job.searchLabel();
		this.title = job.title().strip();
		this.company = job.company();
		this.location = job.location();
		this.salary = job.salary();
		this.link = link;
		this.source = source;
		this.description = job.description();
		this.note = job.note();
		this.postedOn = job.postedOn();
	}

	/**
	 * @param applicationId the application it became, when saved or applied to
	 */
	public void decide(Decision decision, @Nullable Long applicationId) {
		this.decision = decision;
		this.decidedAt = (decision == Decision.PENDING) ? null : Instant.now();
		this.applicationId = applicationId;
	}

}
