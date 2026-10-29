package com.jobwarden.discover.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.search.Recency;
import com.jobwarden.search.WorkType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A request to Claude, with what to look for, each list a text array like a saved search's.
 */
@Entity
@Table(name = "ai_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	@Enumerated(EnumType.STRING)
	private AiRequestKind kind;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> titles = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> locations = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> skills = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> excludes = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> workTypes = new ArrayList<>();

	private @Nullable String seniority;

	private @Nullable String minSalary;

	private @Nullable String notes;

	@Enumerated(EnumType.STRING)
	private Recency recency;

	private boolean useCv;

	private int maxJobs;

	@Enumerated(EnumType.STRING)
	private AiRequestStatus status = AiRequestStatus.WAITING;

	private @Nullable String summary;

	@CreationTimestamp
	private Instant createdAt;

	private @Nullable Instant startedAt;

	private @Nullable Instant finishedAt;

	/** The job it's about, for a kind that is about one */
	private @Nullable Long applicationId;

	public AiRequestEntity(long userId, AiRequestKind kind, AiRequirements requirements, @Nullable Long applicationId) {
		this.userId = userId;
		this.kind = kind;
		this.applicationId = applicationId;
		this.titles = new ArrayList<>(requirements.titles());
		this.locations = new ArrayList<>(requirements.locations());
		this.skills = new ArrayList<>(requirements.skills());
		this.excludes = new ArrayList<>(requirements.excludes());
		this.workTypes = new ArrayList<>(requirements.workTypes().stream().map(Enum::name).sorted().toList());
		this.seniority = requirements.seniority();
		this.minSalary = requirements.minSalary();
		this.notes = requirements.notes();
		this.recency = requirements.recency();
		this.useCv = requirements.useCv();
		this.maxJobs = requirements.maxJobs();
	}

	public AiRequirements toRequirements() {
		return AiRequirements.builder()
			.titles(this.titles)
			.locations(this.locations)
			.skills(this.skills)
			.excludes(this.excludes)
			.workTypes(Set.copyOf(this.workTypes.stream().map(name -> Enum.valueOf(WorkType.class, name)).toList()))
			.seniority(this.seniority)
			.minSalary(this.minSalary)
			.notes(this.notes)
			.recency(this.recency)
			.useCv(this.useCv)
			.maxJobs(this.maxJobs)
			.build();
	}

	/**
	 * Claude picked it up. Once it's working, it stays as it is: Claude may read it again.
	 */
	public void start() {
		if (this.status == AiRequestStatus.WAITING) {
			this.status = AiRequestStatus.WORKING;
			this.startedAt = Instant.now();
		}
	}

	/**
	 * @param status {@link AiRequestStatus#DONE}, {@link AiRequestStatus#FAILED} or {@link AiRequestStatus#CANCELLED}
	 */
	public void finish(AiRequestStatus status, @Nullable String summary) {
		this.status = status;
		this.summary = summary;
		this.finishedAt = Instant.now();
		if (this.startedAt == null && status != AiRequestStatus.CANCELLED) {
			this.startedAt = this.finishedAt;
		}
	}

}
