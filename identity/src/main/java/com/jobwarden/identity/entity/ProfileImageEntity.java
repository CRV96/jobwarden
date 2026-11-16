package com.jobwarden.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A profile's photo, as the pages show it: a small square PNG.
 */
@Entity
@Table(name = "profile_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class ProfileImageEntity {

	@Id
	private Long userId;

	private byte[] image;

	public ProfileImageEntity(long userId, byte[] image) {
		this.userId = userId;
		this.image = image.clone();
	}

	public byte[] getImage() {
		return this.image.clone();
	}

	public void replace(byte[] image) {
		this.image = image.clone();
	}

}
