package com.jobwarden.web.controller;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.constants.HtmxHeaders;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.user.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * What every page's layout shows, for each handler of a {@link PageController}: the selected profile (or {@code null})
 * in the header, which is the only time a request looks it up ({@link Pages#requireProfile} reads it from the model).
 * <p>
 * The sidebar also lists the profiles to switch to, counts the profile's applications, the found jobs waiting on the
 * AI Assistant page and the emails waiting in the inbox, and says when a newer JobWarden is out. HTMX requests skip
 * those: they only swap a part of the page, without the sidebar. Without the updates or the inbox (a test of one
 * controller), the sidebar says nothing of them.
 */
@ControllerAdvice(annotations = PageController.class)
@RequiredArgsConstructor
class LayoutAdvice {

	private final CurrentUser currentUser;

	private final UserService users;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final ObjectProvider<UpdateService> updates;

	private final ObjectProvider<InboxService> inbox;

	@ModelAttribute
	void layout(Model model, @RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx) {
		User profile = this.currentUser.get().orElse(null);
		model.addAttribute(ModelAttributes.CURRENT_PROFILE, profile);
		if (htmx != null) {
			return;
		}
		model.addAttribute(ModelAttributes.SIDEBAR_PROFILES, this.users.findAll());
		model.addAttribute(ModelAttributes.PROFILES_WITH_PASSWORD, this.users.findIdsWithPassword());
		this.updates.ifAvailable(updateService -> model.addAttribute(ModelAttributes.UPDATE, updateService.status()));
		if (profile != null) {
			model.addAttribute(ModelAttributes.APPLICATION_COUNT, this.applications.count(profile.id()));
			model.addAttribute(ModelAttributes.DISCOVER_COUNT, this.discovery.countToReview(profile.id(), null));
			this.inbox.ifAvailable(
					inboxService -> model.addAttribute(ModelAttributes.INBOX_COUNT, inboxService.countPending(profile.id())));
		}
	}

}
