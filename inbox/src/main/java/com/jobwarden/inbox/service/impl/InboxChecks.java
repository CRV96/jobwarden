package com.jobwarden.inbox.service.impl;

import com.jobwarden.inbox.service.InboxService;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads the mailboxes whose time has come, each as often as its profile said in the settings: it looks every minute
 * unless {@code jobwarden.inbox.tick} says otherwise, the first time a couple of minutes after the server starts.
 */
@Component
@RequiredArgsConstructor
class InboxChecks {

	private final InboxService inbox;

	@Scheduled(initialDelayString = "${jobwarden.inbox.first-check:PT2M}", fixedDelayString = "${jobwarden.inbox.tick:PT1M}")
	void checkDue() {
		this.inbox.checkDue();
	}

}
