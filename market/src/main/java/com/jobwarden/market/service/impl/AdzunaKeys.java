package com.jobwarden.market.service.impl;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.jobwarden.market.entity.AdzunaKeyEntity;
import com.jobwarden.market.repository.AdzunaKeyRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The key Adzuna's API is asked with: the one saved in Settings, else the environment's (the ADZUNA_APP_ID and
 * ADZUNA_APP_KEY variables, through {@code jobwarden.market.adzuna.app-id} and {@code app-key}). The saved one is read
 * once and kept until it changes, as the Salaries page asks for it once per country.
 */
@Component
class AdzunaKeys {

	private final AdzunaKeyRepository repository;

	private final @Nullable Key environment;

	/** The saved key once read: empty when there's none; null until it's read */
	private final AtomicReference<@Nullable Optional<Key>> saved = new AtomicReference<>();

	AdzunaKeys(AdzunaKeyRepository repository, @Value("${jobwarden.market.adzuna.app-id:}") String appId,
			@Value("${jobwarden.market.adzuna.app-key:}") String appKey) {
		this.repository = repository;
		this.environment = (appId.isBlank() || appKey.isBlank()) ? null : new Key(appId.strip(), appKey.strip());
	}

	/**
	 * @return the key to ask with; empty without one
	 */
	Optional<Key> current() {
		return saved().or(this::environment);
	}

	Optional<Key> saved() {
		Optional<Key> known = this.saved.get();
		if (known == null) {
			known = this.repository.findById(AdzunaKeyEntity.ID)
				.map(entity -> new Key(entity.getAppId(), entity.getAppKey()));
			this.saved.set(known);
		}
		return known;
	}

	Optional<Key> environment() {
		return Optional.ofNullable(this.environment);
	}

	/** The saved key changed: it's read again next time. */
	void forget() {
		this.saved.set(null);
	}

	/**
	 * @param appKey a secret: it goes in requests to Adzuna, never in a log
	 */
	record Key(String appId, String appKey) {

		@Override
		public String toString() {
			return "Key[appId=" + this.appId + "]";
		}

	}

}
