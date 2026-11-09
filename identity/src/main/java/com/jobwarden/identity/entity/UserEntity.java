package com.jobwarden.identity.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
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

	public UserEntity(String name) {
		this.name = name;
	}

	public void imageChanged(@Nullable Instant at) {
		this.imageChangedAt = at;
	}

}
