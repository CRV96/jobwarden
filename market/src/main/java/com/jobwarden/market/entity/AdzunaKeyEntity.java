package com.jobwarden.market.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The app's key for Adzuna's API, as entered in Settings: there's one at most, the row with {@link #ID}.
 */
@Entity
@Table(name = "market_adzuna_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdzunaKeyEntity {

	/** The one row's id */
	public static final short ID = 1;

	@Id
	private Short id;

	private String appId;

	private String appKey;

	private Instant savedAt;

	public AdzunaKeyEntity(String appId, String appKey) {
		this.id = ID;
		replace(appId, appKey);
	}

	public void replace(String appId, String appKey) {
		this.appId = appId;
		this.appKey = appKey;
		this.savedAt = Instant.now();
	}

}
