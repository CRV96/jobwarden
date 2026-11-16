package com.jobwarden.jobs.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.jobs.dto.NegotiationBrief;
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
 * A job's negotiation brief. Plain ids, like {@link InterviewBriefEntity}'s, so loading an application never loads it.
 */
@Entity
@Table(name = "negotiation_briefs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class NegotiationBriefEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private long applicationId;

	private String position;

	private @Nullable String target;

	private @Nullable String walkAway;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> leverage = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> asks = new ArrayList<>();

	private @Nullable String script;

	private @Nullable String email;

	private @Nullable String watchOut;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> sources = new ArrayList<>();

	private Instant updatedAt;

	public NegotiationBriefEntity(long userId, long applicationId) {
		this.userId = userId;
		this.applicationId = applicationId;
	}

	/**
	 * The brief written again: a job keeps one, the latest.
	 */
	public void replace(NegotiationBrief brief) {
		this.position = brief.position();
		this.target = brief.target();
		this.walkAway = brief.walkAway();
		this.leverage = new ArrayList<>(brief.leverage());
		this.asks = new ArrayList<>(brief.asks());
		this.script = brief.script();
		this.email = brief.email();
		this.watchOut = brief.watchOut();
		this.sources = new ArrayList<>(brief.sources());
		this.updatedAt = Instant.now();
	}

}
