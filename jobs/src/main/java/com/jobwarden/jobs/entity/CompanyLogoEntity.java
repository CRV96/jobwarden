package com.jobwarden.jobs.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An application's company logo, as a PNG. In a table of its own, so loading an application doesn't load its logo:
 * the pages fetch logos separately, as images.
 */
@Entity
@Table(name = "company_logos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class CompanyLogoEntity {

	// The application's id: a plain id, like ApplicationEntity's userId, so the application never loads its logo
	@Id
	private Long applicationId;

	private byte[] image;

	@UpdateTimestamp
	private Instant updatedAt;

	public CompanyLogoEntity(long applicationId, byte[] image) {
		this.applicationId = applicationId;
		this.image = image;
	}

	/**
	 * The logo as the posting shows it now: companies change theirs now and then.
	 */
	public void changeImage(byte[] image) {
		this.image = image;
	}

}
