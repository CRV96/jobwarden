package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.jobs.dto.CompanyResearch;
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
 * What the AI Assistant found out about a job's company. Plain ids, like {@link InterviewBriefEntity}'s, so loading an
 * application never loads it.
 */
@Entity
@Table(name = "company_research")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CompanyResearchEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	// null until it's saved
	private @Nullable Long id;

	private long userId;

	private long applicationId;

	private String company;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> ratings = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> pros = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> cons = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> salaries = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> benefits = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> news = new ArrayList<>();

	private @Nullable String interviews;

	private @Nullable String watchOut;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> sources = new ArrayList<>();

	private Instant updatedAt;

	public CompanyResearchEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The research done again: a job keeps one, the latest.
	 */
	public void replace(CompanyResearch research, Instant now) {
		this.company = research.company();
		this.ratings = new ArrayList<>(research.ratings());
		this.pros = new ArrayList<>(research.pros());
		this.cons = new ArrayList<>(research.cons());
		this.salaries = new ArrayList<>(research.salaries());
		this.benefits = new ArrayList<>(research.benefits());
		this.news = new ArrayList<>(research.news());
		this.interviews = research.interviews();
		this.watchOut = research.watchOut();
		this.sources = new ArrayList<>(research.sources());
		this.updatedAt = now;
	}

}
