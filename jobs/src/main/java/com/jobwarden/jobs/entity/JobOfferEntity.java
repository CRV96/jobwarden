package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.jobwarden.jobs.dto.JobOffer;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The terms of a job's offer. Plain ids, like {@link InterviewBriefEntity}'s, so loading an application never loads it.
 */
@Entity
@Table(name = "job_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class JobOfferEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private @Nullable String baseSalary;

	private @Nullable String bonus;

	private @Nullable String equity;

	private @Nullable String signingBonus;

	private @Nullable String timeOff;

	private @Nullable String benefits;

	private @Nullable LocalDate startDate;

	private @Nullable LocalDate respondBy;

	private @Nullable String notes;

	private Instant updatedAt;

	public JobOfferEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The terms typed in again: a job keeps one set, the latest.
	 */
	public void replace(JobOffer offer) {
		this.baseSalary = offer.baseSalary();
		this.bonus = offer.bonus();
		this.equity = offer.equity();
		this.signingBonus = offer.signingBonus();
		this.timeOff = offer.timeOff();
		this.benefits = offer.benefits();
		this.startDate = offer.startDate();
		this.respondBy = offer.respondBy();
		this.notes = offer.notes();
		this.updatedAt = Instant.now();
	}

}
