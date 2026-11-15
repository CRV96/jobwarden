package com.jobwarden.capture.controller;

import java.util.List;
import java.util.Locale;

import com.jobwarden.capture.dto.DueReminder;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ReminderService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reminders set on the calendar that are due, which the extension shows as the system's notifications, about once
 * a minute while the browser runs: so they come with no JobWarden page open. Each comes once, to the extension or to
 * a page, whichever asks first. In the language of the request, like the API's errors. Like
 * {@link CaptureController}, the extensions in Firefox and Chrome may call it, and web pages may not.
 */
@RestController
@RequestMapping("${jobwarden.capture.path}/reminders")
@RequiredArgsConstructor
class RemindersController {

	private final ReminderService reminders;

	private final UserService users;

	/**
	 * Takes them, so a page asking next doesn't show them again: hence a POST.
	 */
	@PostMapping("/due")
	List<DueReminder> due(@RequestParam long userId, Locale locale) {
		if (!this.users.exists(userId)) {
			throw new UserNotFoundException(userId);
		}
		return this.reminders.claimDue(userId, locale)
			.stream()
			.map(reminder -> new DueReminder(reminder.id(), reminder.applicationId(), reminder.title(), reminder.body()))
			.toList();
	}

}
