package com.jobwarden.jobs.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import com.jobwarden.jobs.entity.CalendarFeedEntity;
import com.jobwarden.jobs.repository.CalendarFeedRepository;
import com.jobwarden.jobs.service.CalendarFeedService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@CustomLog
@RequiredArgsConstructor
class DefaultCalendarFeedService implements CalendarFeedService {

	/** 32 random bytes: as hard to guess as a password can be, and 43 characters in an address. */
	private static final int TOKEN_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final CalendarFeedRepository feeds;

	@Override
	public String token(long userId) {
		return this.feeds.findById(userId).map(CalendarFeedEntity::getToken).orElseGet(() -> {
			String token = newToken();
			this.feeds.save(new CalendarFeedEntity(userId, token));
			log.info("Made the calendar feed's address of profile {}", userId);
			return token;
		});
	}

	@Override
	public String renewToken(long userId) {
		String token = newToken();
		this.feeds.findById(userId)
			.ifPresentOrElse(feed -> feed.changeToken(token), () -> this.feeds.save(new CalendarFeedEntity(userId, token)));
		log.info("Made a new calendar feed's address for profile {}", userId);
		return token;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> findOwner(String token) {
		return this.feeds.findByToken(token).map(CalendarFeedEntity::getUserId);
	}

	private static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
