package com.jobwarden.identity.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.enums.IdentityErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProfilePasswordsTests {

	private final MovingClock clock = new MovingClock();

	private final ProfilePasswords passwords = new ProfilePasswords(this.clock);

	@Test
	void keepsAHashWithItsAlgorithmAndASaltOfItsOwnNeverThePassword() {
		String hash = this.passwords.hash("correct horse");

		assertThat(hash).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain("correct horse");
		assertThat(this.passwords.hash("correct horse")).as("another salt").isNotEqualTo(hash);
		assertThatCode(() -> this.passwords.check(1, hash, "correct horse")).doesNotThrowAnyException();
		assertThatCode(() -> this.passwords.check(1, hash, "Correct horse")).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void fiveWrongInARowMakeTheProfileWaitAMinuteTheRightPasswordToo() {
		String hash = this.passwords.hash("secret1");
		for (int i = 1; i < ProfilePasswords.TRIES; i++) {
			assertRefused(() -> this.passwords.check(1, hash, "wrong"), IdentityErrorCode.PASSWORD_WRONG);
		}
		assertRefused(() -> this.passwords.check(1, hash, "wrong"), IdentityErrorCode.PASSWORD_TRIES_EXCEEDED);
		assertRefused(() -> this.passwords.check(1, hash, "secret1"), IdentityErrorCode.PASSWORD_TRIES_EXCEEDED);
		assertThatCode(() -> this.passwords.check(2, hash, "secret1")).as("another profile").doesNotThrowAnyException();

		this.clock.advance(ProfilePasswords.WAIT);

		assertThatCode(() -> this.passwords.check(1, hash, "secret1")).doesNotThrowAnyException();
	}

	@Test
	void theRightPasswordStartsTheCountAgain() {
		String hash = this.passwords.hash("secret1");
		for (int i = 1; i < ProfilePasswords.TRIES; i++) {
			assertRefused(() -> this.passwords.check(1, hash, "wrong"), IdentityErrorCode.PASSWORD_WRONG);
		}
		this.passwords.check(1, hash, "secret1");

		assertRefused(() -> this.passwords.check(1, hash, "wrong"), IdentityErrorCode.PASSWORD_WRONG);
	}

	@Test
	void noPasswordIsAWrongOne() {
		String hash = this.passwords.hash("secret1");

		assertRefused(() -> this.passwords.check(1, hash, null), IdentityErrorCode.PASSWORD_WRONG);
	}

	private static void assertRefused(Runnable check, IdentityErrorCode code) {
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(check::run)
			.satisfies(refused -> assertThat(refused.getErrorCode()).isEqualTo(code));
	}

	/** A clock the test moves on. */
	private static final class MovingClock extends Clock {

		private Instant now = Instant.parse("2026-11-16T09:00:00Z");

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
