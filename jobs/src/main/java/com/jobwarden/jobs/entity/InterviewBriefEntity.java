package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.jobs.dto.InterviewBrief;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A job's interview brief. Plain ids, like {@link ReminderEntity}'s, so loading an application never loads it.
 */
@Entity
@Table(name = "interview_briefs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class InterviewBriefEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private String company;

	private @Nullable String role;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> questions = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> stories = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> askThem = new ArrayList<>();

	private @Nullable String watchOut;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> sources = new ArrayList<>();

	private Instant updatedAt;

	public InterviewBriefEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The brief written again: a job keeps one, the latest.
	 */
	public void replace(InterviewBrief brief) {
		this.company = brief.company();
		this.role = brief.role();
		this.questions = new ArrayList<>(brief.questions());
		this.stories = new ArrayList<>(brief.stories());
		this.askThem = new ArrayList<>(brief.askThem());
		this.watchOut = brief.watchOut();
		this.sources = new ArrayList<>(brief.sources());
		this.updatedAt = Instant.now();
	}

}
