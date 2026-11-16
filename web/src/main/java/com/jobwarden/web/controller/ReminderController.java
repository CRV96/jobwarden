package com.jobwarden.web.controller;

import com.jobwarden.jobs.enums.ReminderLead;
import com.jobwarden.jobs.service.ReminderService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.ReminderForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The reminders of what the calendar shows: set from the calendar's Coming up list, and from a job's side panel and
 * page, which list its interviews, deadline and day to follow up from today on. They come as notifications, which
 * {@link ReminderNoticeController} gives the pages.
 */
@Controller
@RequiredArgsConstructor
class ReminderController extends BaseController {

	private final ReminderService reminders;

	/**
	 * A job's entries with their reminders: HTMX loads them after the job, and again when its follow-up dates change.
	 */
	@GetMapping(ControllerConstants.APPLICATIONS + "/{id}/reminders")
	String section(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		model.addAttribute(ModelAttributes.REMINDER_ENTRIES, this.reminders.findRemindable(userId, id));
		model.addAttribute(ModelAttributes.REMINDER_JOB_ID, id);
		return Views.REMINDERS_FRAGMENT;
	}

	/**
	 * Called by HTMX as an entry's chips change: the ones ticked are set, the others taken away. Nothing to swap, the
	 * chips already show it.
	 */
	@PostMapping(ControllerConstants.REMINDERS)
	ResponseEntity<Void> set(@Valid ReminderForm form, Model model) {
		long userId = requireProfile(model).id();
		for (ReminderLead lead : ReminderLead.values()) {
			this.reminders.set(userId, form.toTarget(), lead, form.toLeads().contains(lead));
		}
		return ResponseEntity.noContent().build();
	}

}
