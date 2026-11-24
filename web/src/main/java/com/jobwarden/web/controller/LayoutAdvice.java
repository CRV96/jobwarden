package com.jobwarden.web.controller;

import java.time.Instant;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.constants.HtmxHeaders;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.enums.JobsView;
import com.jobwarden.web.user.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * What every page's layout shows, for each handler of a {@link PageController}: the selected profile (or {@code null})
 * in the header, which is the only time a request looks it up ({@link Pages#requireProfile} reads it from the model).
 * <p>
 * The sidebar also lists the profiles to switch to, counts the profile's applications, the found jobs waiting on the
 * AI Assistant page and the emails waiting in the inbox, opens the Jobs page as the list or the board, as it was left,
 * and says when a newer JobWarden is out; with a profile, whether it has set up everything (the menu offers the setup
 * guide until it has), and the release running, for the popup that tells what changed
 * once after an update (whats-new.js). HTMX requests skip
 * those: they only swap a part of the page, without the sidebar. Without the updates or the inbox (a test of one
 * controller), the sidebar says nothing of them; without the setup's steps, the menu offers the guide.
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

	private final ObjectProvider<SetupSteps> setupSteps;

	@ModelAttribute
	void layout(Model model, @RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx,
			@CookieValue(name = JobsView.COOKIE, required = false) @Nullable String jobsView) {
		User profile = this.currentUser.get().orElse(null);
		model.addAttribute(ModelAttributes.CURRENT_PROFILE, profile);
		if (htmx != null) {
			return;
		}
		model.addAttribute(ModelAttributes.SIDEBAR_PROFILES, this.users.findAll());
		// The Jobs page in the view it was left in: the list or the board
		model.addAttribute(ModelAttributes.JOBS_URL, JobsView.of(jobsView).getPath());
		model.addAttribute(ModelAttributes.PROFILES_WITH_PASSWORD, this.users.findIdsWithPassword());
		this.updates.ifAvailable(updateService -> model.addAttribute(ModelAttributes.UPDATE, updateService.status()));
		if (profile != null) {
			model.addAttribute(ModelAttributes.APPLICATION_COUNT, this.applications.count(profile.id()));
			model.addAttribute(ModelAttributes.DISCOVER_COUNT, this.discovery.countToReview(profile.id(), null));
			this.inbox.ifAvailable(
					inboxService -> model.addAttribute(ModelAttributes.INBOX_COUNT, inboxService.countPending(profile.id())));
			this.setupSteps.ifAvailable(
					steps -> model.addAttribute(ModelAttributes.SETUP_COMPLETE, steps.complete(profile.id())));
			this.updates.ifAvailable(updateService -> updateService.running().ifPresent(release -> {
				model.addAttribute(ModelAttributes.RUNNING_RELEASE, release);
				model.addAttribute(ModelAttributes.PROFILE_AFTER_RELEASE, madeAfter(profile, release));
			}));
		}
	}

	/**
	 * Whether the profile was made after the release came out, so on a server that already ran it: it has no update to
	 * be told of. False when GitHub didn't say when the release came out.
	 */
	private static boolean madeAfter(User profile, Release release) {
		Instant published = release.publishedAt();
		return published != null && profile.createdAt().isAfter(published);
	}

}
