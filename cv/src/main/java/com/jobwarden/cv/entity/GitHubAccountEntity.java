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
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A profile's GitHub account, its token encrypted, and the repositories it chose.
 */
@Entity
@Table(name = "github_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class GitHubAccountEntity {

	@Id
	private Long userId;

	private String username;

	// Encrypted (Secrets): never the token as typed
	private @Nullable String token;

	@JdbcTypeCode(SqlTypes.ARRAY)
	private List<String> repositories = new ArrayList<>();

	private Instant connectedAt;

	public GitHubAccountEntity(long userId) {
		this.userId = userId;
	}

	/**
	 * Another account, or the same with another token: the repositories chosen go, as the token may not read them.
	 */
	public void connect(String username, @Nullable String encryptedToken) {
		this.username = username;
		this.token = encryptedToken;
		this.repositories = new ArrayList<>();
		this.connectedAt = Instant.now();
	}

	public void choose(List<String> repositories) {
		this.repositories = new ArrayList<>(repositories);
	}

}
