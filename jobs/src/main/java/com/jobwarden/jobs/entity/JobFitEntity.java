package com.jobwarden.jobs.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * How well the profile fits a job. Plain ids, like {@link InterviewBriefEntity}'s, so loading an application never
 * loads it.
 */
@Entity
@Table(name = "job_fits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class JobFitEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private BigDecimal score;

	private @Nullable String summary;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "job_fit_requirements", joinColumns = @JoinColumn(name = "job_fit_id"))
	@OrderColumn(name = "position")
	private List<JobFitRequirementEmbeddable> requirements = new ArrayList<>();

	private Instant updatedAt;

	public JobFitEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The job evaluated again: it keeps one fit, the latest.
	 */
	public void replace(BigDecimal score, @Nullable String summary, List<JobFitRequirementEmbeddable> requirements) {
		this.score = score;
		this.summary = summary;
		this.requirements.clear();
		this.requirements.addAll(requirements);
		this.updatedAt = Instant.now();
	}

	public List<JobFitRequirementEmbeddable> getRequirements() {
		return Collections.unmodifiableList(this.requirements);
	}

}
