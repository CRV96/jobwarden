package com.jobwarden.market.service.impl;

import java.util.Optional;

import com.jobwarden.market.dto.AdzunaKey;
import com.jobwarden.market.entity.AdzunaKeyEntity;
import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.enums.AdzunaKeySource;
import com.jobwarden.market.repository.AdzunaKeyRepository;
import com.jobwarden.market.service.AdzunaKeyService;
import com.jobwarden.market.service.impl.AdzunaKeys.Key;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saving asks Adzuna first, outside a transaction, as that takes up to a few seconds. Saving and removing forget the
 * key in memory only once the change is in the database: a request in between can't keep the old one.
 */
@Service
@CustomLog
@RequiredArgsConstructor
class DefaultAdzunaKeyService implements AdzunaKeyService {

	private final AdzunaKeyRepository repository;

	private final AdzunaKeys keys;

	private final AdzunaSalaries adzuna;

	@Override
	@Transactional(readOnly = true)
	public AdzunaKey find() {
		Optional<AdzunaKeyEntity> saved = this.repository.findById(AdzunaKeyEntity.ID);
		if (saved.isPresent()) {
			return AdzunaKey.of(AdzunaKeySource.SETTINGS, saved.get().getAppId(), saved.get().getAppKey(),
					saved.get().getSavedAt());
		}
		return this.keys.environment()
			.map(key -> AdzunaKey.of(AdzunaKeySource.ENVIRONMENT, key.appId(), key.appKey(), null))
			.orElse(AdzunaKey.NONE);
	}

	@Override
	public AdzunaKeyCheck save(String appId, String appKey) {
		if (appId.isBlank() || appKey.isBlank()) {
			throw new IllegalArgumentException("An Adzuna key needs its app ID and its app key");
		}
		Key key = new Key(appId.strip(), appKey.strip());
		AdzunaKeyCheck check = this.adzuna.check(key);
		if (check == AdzunaKeyCheck.REFUSED) {
			log.info("Adzuna refused the key with app ID {}: not saved", key.appId());
			return check;
		}
		AdzunaKeyEntity entity = this.repository.findById(AdzunaKeyEntity.ID)
			.orElseGet(() -> new AdzunaKeyEntity(key.appId(), key.appKey()));
		entity.replace(key.appId(), key.appKey());
		this.repository.save(entity);
		this.keys.forget();
		this.adzuna.forgetAnswers();
		log.info("Saved the Adzuna key with app ID {} ({})", key.appId(), check);
		return check;
	}

	@Override
	public void remove() {
		if (this.repository.existsById(AdzunaKeyEntity.ID)) {
			this.repository.deleteById(AdzunaKeyEntity.ID);
			log.info("Removed the Adzuna key saved in Settings");
		}
		this.keys.forget();
		this.adzuna.forgetAnswers();
	}

}
