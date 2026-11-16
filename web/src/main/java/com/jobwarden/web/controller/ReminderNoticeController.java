package com.jobwarden.web.controller;

import java.util.List;
import java.util.Locale;

import com.jobwarden.jobs.dto.ReminderNotice;
import com.jobwarden.jobs.service.ReminderService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.user.CurrentUser;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The reminders that are due, for app.js to show: every page asks about once a minute while a profile is selected.
 * Each reminder comes once, to whichever asks first (a tab, or the browser extension). Not a page: no sidebar to fill
 * in, so it skips {@link BaseController}, and answers with nothing when no profile is selected.
 */
@RestController
@RequiredArgsConstructor
class ReminderNoticeController {

	private final CurrentUser currentUser;

	private final ReminderService reminders;

	@PostMapping(ControllerConstants.REMINDERS + "/due")
	List<Notice> due(Locale locale) {
		return this.currentUser.get()
			.map(profile -> this.reminders.claimDue(profile.id(), locale).stream().map(ReminderNoticeController::notice).toList())
			.orElse(List.of());
	}

	private static Notice notice(ReminderNotice reminder) {
		String url = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path(ControllerConstants.APPLICATIONS + "/{id}")
			.buildAndExpand(reminder.applicationId())
			.toUriString();
		return new Notice(reminder.id(), reminder.title(), reminder.body(), url);
	}

	/**
	 * @param url the job's page, which a click on the notification opens
	 */
	record Notice(long id, String title, String body, String url) {
	}

}
