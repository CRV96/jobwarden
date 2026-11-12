package com.jobwarden.jobs.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.enums.SalaryPeriod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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

	private @Nullable BigDecimal baseSalaryAmount;

	private @Nullable String baseSalaryCurrency;

	@Enumerated(EnumType.STRING)
	private @Nullable SalaryPeriod baseSalaryPeriod;

	/** The base salary in words, as terms saved before it was a figure */
	private @Nullable String baseSalary;

	/** What it pays on top, in the order typed in */
	@ElementCollection
	@CollectionTable(name = "job_offer_extras", joinColumns = @JoinColumn(name = "offer_id"))
	@OrderColumn(name = "position")
	@Getter(AccessLevel.NONE)
	private List<OfferExtraValue> extras = new ArrayList<>();

	private @Nullable String timeOff;

	private @Nullable String benefits;

	private @Nullable LocalDate startDate;

	private @Nullable LocalDate respondBy;

	private @Nullable String notes;

	private Instant updatedAt;

	/**
	 * @return what it pays on top, in the order typed in
	 */
	public List<OfferExtra> extras() {
		return this.extras.stream().map(OfferExtraValue::toExtra).toList();
	}

	public JobOfferEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The terms typed in again: a job keeps one set, the latest.
	 */
	public void replace(JobOffer offer) {
		this.baseSalaryAmount = offer.baseSalaryAmount();
		this.baseSalaryCurrency = offer.baseSalaryCurrency();
		this.baseSalaryPeriod = offer.baseSalaryPeriod();
		this.baseSalary = offer.baseSalary();
		this.extras.clear();
		offer.extras().stream().map(OfferExtraValue::new).forEach(this.extras::add);
		this.timeOff = offer.timeOff();
		this.benefits = offer.benefits();
		this.startDate = offer.startDate();
		this.respondBy = offer.respondBy();
		this.notes = offer.notes();
		this.updatedAt = Instant.now();
	}

}
