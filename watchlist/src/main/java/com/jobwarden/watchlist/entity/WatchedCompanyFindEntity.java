package com.jobwarden.watchlist.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A found job a company's board gave.
 */
@Entity
@Table(name = "watched_company_finds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class WatchedCompanyFindEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long watchedCompanyId;

	private long foundJobId;

	private Instant foundAt;

	public WatchedCompanyFindEntity(long watchedCompanyId, long foundJobId) {
		this.watchedCompanyId = watchedCompanyId;
		this.foundJobId = foundJobId;
		this.foundAt = Instant.now();
	}

}
