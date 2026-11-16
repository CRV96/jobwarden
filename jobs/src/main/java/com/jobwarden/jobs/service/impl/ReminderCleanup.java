package com.jobwarden.jobs.service.impl;

import java.util.concurrent.TimeUnit;

import com.jobwarden.jobs.service.ReminderService;
import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes the reminders whose entry is over, now and then: those of a profile whose pages and extension ask for its
 * reminders go as they ask, those of the others here.
 */
@Component
@RequiredArgsConstructor
class ReminderCleanup {

	private final ReminderService reminders;

	@Scheduled(initialDelay = 5, fixedDelay = 6 * 60, timeUnit = TimeUnit.MINUTES)
	void deleteOver() {
		this.reminders.deleteOver();
	}

}
