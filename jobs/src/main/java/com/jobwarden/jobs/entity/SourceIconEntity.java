package com.jobwarden.jobs.entity;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The icon of a site a profile saved postings from, as a PNG. Kept per site rather than per application: every
 * posting from a site shows the same one.
 */
@Entity
@Table(name = "source_icons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class SourceIconEntity {

	@EmbeddedId
	private SourceIconKey key;

	private byte[] image;

	@UpdateTimestamp
	private Instant updatedAt;

	public SourceIconEntity(SourceIconKey key, byte[] image) {
		this.key = key;
		this.image = image;
	}

	/**
	 * The site's icon as the browser shows it now: sites change theirs now and then.
	 */
	public void changeImage(byte[] image) {
		this.image = image;
	}

}
