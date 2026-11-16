package com.jobwarden.identity.entity;

import java.time.Instant;
import java.util.UUID;

import com.jobwarden.identity.enums.Assistant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@CreationTimestamp
	private Instant createdAt;

	/** When its photo last changed; {@code null} without one */
	private @Nullable Instant imageChangedAt;

	/** The id it shows, made when it's created: the database's numbers are only for JobWarden itself */
	@Column(updatable = false)
	private UUID publicId = UUID.randomUUID();

	/** The AI assistant it works with */
	@Enumerated(EnumType.STRING)
	private Assistant assistant = Assistant.DEFAULT;

	/** Its password's hash, with the algorithm's id in front; {@code null} without a password */
	private @Nullable String passwordHash;

	/**
	 * A profile to save: the database gives its id as it's saved (IDENTITY), which is why the build leaves entities'
	 * nullness to JPA (NullAway's ExcludedClassAnnotations) and SonarCloud's S2637 is answered here.
	 */
	@SuppressWarnings("java:S2637")
	public UserEntity(String name) {
		this.name = name;
	}

	public void imageChanged(@Nullable Instant at) {
		this.imageChangedAt = at;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changeAssistant(Assistant assistant) {
		this.assistant = assistant;
	}

	/**
	 * @param hash the new password's hash, or {@code null} to have none
	 */
	public void changePasswordHash(@Nullable String hash) {
		this.passwordHash = hash;
	}

}
