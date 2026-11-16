package com.jobwarden.identity.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.enums.IdentityErrorCode;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The profiles' passwords: hashed with PBKDF2 (Spring Security's defaults: a random salt, 310,000 rounds of SHA-256),
 * the algorithm's id in front of the hash, so a stronger one can follow and the old hashes still match. Wrong tries are
 * counted: {@link #TRIES} in a row make the profile wait {@link #WAIT}, the right password too. They're kept in memory:
 * a restart forgets them, which suits a server whose tries come from someone at its pages.
 */
@Component
@CustomLog
@RequiredArgsConstructor
class ProfilePasswords {

	static final int TRIES = 5;

	static final Duration WAIT = Duration.ofMinutes(1);

	private static final String ALGORITHM = "pbkdf2@SpringSecurity_v5_8";

	private final PasswordEncoder encoder = new DelegatingPasswordEncoder(ALGORITHM,
			Map.of(ALGORITHM, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));

	private final Map<Long, Tries> wrong = new ConcurrentHashMap<>();

	private final Clock clock;

	String hash(String password) {
		return Objects.requireNonNull(this.encoder.encode(password));
	}

	/**
	 * @throws BusinessRuleException PASSWORD_TRIES_EXCEEDED while the profile waits, PASSWORD_WRONG when it isn't the
	 * password: the last of the tries in a row makes it wait, and says so
	 */
	void check(long userId, String hash, @Nullable String password) {
		Instant now = this.clock.instant();
		Tries before = this.wrong.get(userId);
		if (before != null && before.waitsAt(now)) {
			throw exceeded(userId);
		}
		if (password != null && this.encoder.matches(password, hash)) {
			this.wrong.remove(userId);
			return;
		}
		int count = (before != null && before.until() == null) ? before.count() + 1 : 1;
		if (count >= TRIES) {
			this.wrong.put(userId, new Tries(0, now.plus(WAIT)));
			log.warn("Profile {}: {} wrong passwords in a row, waiting {}", userId, count, WAIT);
			throw exceeded(userId);
		}
		this.wrong.put(userId, new Tries(count, null));
		log.debug("Profile {}: wrong password, {} in a row", userId, count);
		throw new BusinessRuleException(IdentityErrorCode.PASSWORD_WRONG, "Wrong password for profile " + userId);
	}

	private static BusinessRuleException exceeded(long userId) {
		return new BusinessRuleException(IdentityErrorCode.PASSWORD_TRIES_EXCEEDED,
				"Too many wrong passwords for profile " + userId, String.valueOf(WAIT.toMinutes()));
	}

	/**
	 * @param count the wrong tries in a row
	 * @param until till when the profile waits after too many; {@code null} while it doesn't
	 */
	private record Tries(int count, @Nullable Instant until) {

		boolean waitsAt(Instant now) {
			return this.until != null && now.isBefore(this.until);
		}

	}

}
