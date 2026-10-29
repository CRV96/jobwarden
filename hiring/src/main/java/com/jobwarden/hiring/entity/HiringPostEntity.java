package com.jobwarden.hiring.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "hiring_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HiringPostEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recruiter_id")
	private RecruiterEntity recruiter;

	private String link;

	private String text;

	private @Nullable String role;

	private @Nullable String company;

	private @Nullable String country;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> tags = new ArrayList<>();

	private @Nullable LocalDate postedOn;

	@CreationTimestamp
	private Instant foundAt;

	private boolean hidden;

	private @Nullable Long applicationId;

	public HiringPostEntity(long userId, RecruiterEntity recruiter, String link, String text, @Nullable String role,
			@Nullable String company, @Nullable String country, List<String> tags, @Nullable LocalDate postedOn) {
		this.userId = userId;
		this.recruiter = recruiter;
		this.link = link;
		this.text = text;
		this.role = role;
		this.company = company;
		this.country = country;
		this.tags = new ArrayList<>(tags);
		this.postedOn = postedOn;
	}

	public void hide() {
		this.hidden = true;
	}

	public void savedAs(long applicationId) {
		this.applicationId = applicationId;
	}

}
