package com.jobwarden.cv.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.jobwarden.cv.dto.Story;
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
 * A story of the profile's story bank.
 */
@Entity
@Table(name = "cv_stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private String title;

	private @Nullable String situation;

	private @Nullable String task;

	private @Nullable String action;

	private @Nullable String result;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> skills = new ArrayList<>();

	private Instant createdAt;

	private Instant updatedAt;

	public StoryEntity(long userId) {
		this.userId = userId;
		this.createdAt = Instant.now();
	}

	/**
	 * Its parts, told again.
	 */
	public void change(Story story) {
		this.title = story.title();
		this.situation = story.situation();
		this.task = story.task();
		this.action = story.action();
		this.result = story.result();
		this.skills = new ArrayList<>(story.skills());
		this.updatedAt = Instant.now();
	}

}
