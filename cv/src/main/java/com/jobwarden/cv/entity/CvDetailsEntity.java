package com.jobwarden.cv.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * The fields a profile's CV is built from. The lists of entries are JSON, written and read by the service: the
 * entities stay plain text, and the JSON is plain to read in the database.
 */
@Entity
@Table(name = "cv_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CvDetailsEntity {

	@Id
	private Long userId;

	private @Nullable String fullName;

	private @Nullable String headline;

	private @Nullable String email;

	private @Nullable String phone;

	private @Nullable String location;

	private @Nullable String website;

	private @Nullable String linkedin;

	private @Nullable String github;

	private @Nullable String summary;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> skills = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	private String experience = "[]";

	@JdbcTypeCode(SqlTypes.JSON)
	private String education = "[]";

	@JdbcTypeCode(SqlTypes.JSON)
	private String languages = "[]";

	@JdbcTypeCode(SqlTypes.JSON)
	private String certifications = "[]";

	@UpdateTimestamp
	private Instant updatedAt;

	public CvDetailsEntity(long userId) {
		this.userId = userId;
	}

	public void changePersonalDetails(@Nullable String fullName, @Nullable String headline, @Nullable String email,
			@Nullable String phone, @Nullable String location, @Nullable String website, @Nullable String linkedin,
			@Nullable String github, @Nullable String summary) {
		this.fullName = fullName;
		this.headline = headline;
		this.email = email;
		this.phone = phone;
		this.location = location;
		this.website = website;
		this.linkedin = linkedin;
		this.github = github;
		this.summary = summary;
	}

	/**
	 * @param experience the entries as a JSON array, like the others
	 */
	public void changeLists(List<String> skills, String experience, String education, String languages,
			String certifications) {
		this.skills = new ArrayList<>(skills);
		this.experience = experience;
		this.education = education;
		this.languages = languages;
		this.certifications = certifications;
		// The lists alone may not make Hibernate see a change: the timestamp says it, and is set again as it saves
		this.updatedAt = Instant.now();
	}

}
