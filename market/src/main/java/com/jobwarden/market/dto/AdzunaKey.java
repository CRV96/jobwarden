package com.jobwarden.market.dto;

import java.time.Instant;

import com.jobwarden.market.enums.AdzunaKeySource;
import org.jspecify.annotations.Nullable;

/**
 * The key the app asks Adzuna's API with, as Settings shows it: where it comes from, its app ID, and only the end of
 * its app key, which is a secret.
 *
 * @param appId {@code null} without a key
 * @param keyEnding the app key's last characters; {@code null} without a key
 * @param savedAt when it was saved in Settings; {@code null} for the environment's key, or without one
 */
public record AdzunaKey(AdzunaKeySource source, @Nullable String appId, @Nullable String keyEnding,
		@Nullable Instant savedAt) {

	/** No key at all */
	public static final AdzunaKey NONE = new AdzunaKey(AdzunaKeySource.NONE, null, null, null);

	/** How many of the app key's characters show */
	public static final int SHOWN_ENDING = 4;

	/**
	 * @return the key as Settings shows it, with only the app key's end
	 */
	public static AdzunaKey of(AdzunaKeySource source, String appId, String appKey, @Nullable Instant savedAt) {
		String ending = appKey.substring(Math.max(0, appKey.length() - SHOWN_ENDING));
		return new AdzunaKey(source, appId, ending, savedAt);
	}

}
