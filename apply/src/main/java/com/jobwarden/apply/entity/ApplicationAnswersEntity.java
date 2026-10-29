package com.jobwarden.apply.entity;

import java.time.Instant;

import com.jobwarden.apply.enums.CoverLetter;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * The profile's answers. The custom ones are a JSON array, written and read by the service.
 */
@Entity
@Table(name = "application_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAnswersEntity {

	@Id
	private Long userId;

	private @Nullable String workAuthorization;

	private @Nullable Boolean needsSponsorship;

	private @Nullable String noticePeriod;

	private @Nullable String earliestStart;

	private @Nullable String salaryExpectation;

	private @Nullable Boolean willingToRelocate;

	private @Nullable String workPreference;

	private @Nullable Integer yearsOfExperience;

	@Enumerated(EnumType.STRING)
	private CoverLetter coverLetter = CoverLetter.WRITE;

	private boolean declineVoluntary = true;

	@JdbcTypeCode(SqlTypes.JSON)
	private String custom = "[]";

	private Instant updatedAt;

	public ApplicationAnswersEntity(long userId) {
		this.userId = userId;
	}

	/**
	 * @param custom the custom answers as a JSON array
	 */
	public void replace(@Nullable String workAuthorization, @Nullable Boolean needsSponsorship,
			@Nullable String noticePeriod, @Nullable String earliestStart, @Nullable String salaryExpectation,
			@Nullable Boolean willingToRelocate, @Nullable String workPreference, @Nullable Integer yearsOfExperience,
			CoverLetter coverLetter, boolean declineVoluntary, String custom) {
		this.workAuthorization = workAuthorization;
		this.needsSponsorship = needsSponsorship;
		this.noticePeriod = noticePeriod;
		this.earliestStart = earliestStart;
		this.salaryExpectation = salaryExpectation;
		this.willingToRelocate = willingToRelocate;
		this.workPreference = workPreference;
		this.yearsOfExperience = yearsOfExperience;
		this.coverLetter = coverLetter;
		this.declineVoluntary = declineVoluntary;
		this.custom = custom;
		this.updatedAt = Instant.now();
	}

}
