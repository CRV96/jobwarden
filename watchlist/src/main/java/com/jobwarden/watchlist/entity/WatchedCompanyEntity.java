package com.jobwarden.watchlist.entity;

import java.time.Duration;
import java.time.Instant;

import com.jobwarden.watchlist.enums.Ats;
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
import org.jspecify.annotations.Nullable;

/**
 * A company a profile watches, by its board, and how its last reading went.
 */
@Entity
@Table(name = "watched_companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class WatchedCompanyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long userId;

	private String name;

	@Enumerated(EnumType.STRING)
	private Ats ats;

	private String board;

	private Instant addedAt;

	private @Nullable Instant lastCheckedAt;

	private @Nullable String lastError;

	public WatchedCompanyEntity(long userId, String name, Ats ats, String board) {
		this.userId = userId;
		this.name = name;
		this.ats = ats;
		this.board = board;
		this.addedAt = Instant.now();
	}

	public void checked(Instant at) {
		this.lastCheckedAt = at;
		this.lastError = null;
	}

	/**
	 * @param error why the board couldn't be read
	 */
	public void failed(String error, Instant at) {
		this.lastCheckedAt = at;
		this.lastError = error;
	}

	/**
	 * Whether it's time to read its board again: never read yet, or read (or tried) long enough ago.
	 */
	public boolean isDue(Duration every, Instant now) {
		return this.lastCheckedAt == null || !this.lastCheckedAt.plus(every).isAfter(now);
	}

}
