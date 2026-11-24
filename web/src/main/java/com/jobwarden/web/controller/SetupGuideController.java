package com.jobwarden.web.controller;

import java.util.Arrays;
import java.util.List;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.assistant.AssistantConnections.State;
import com.jobwarden.web.assistant.PromptCards;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import lombok.RequiredArgsConstructor;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * The setup guide: what to set up for each part of JobWarden to work, and which of it the profile has done. Its CV, for
 * the AI Assistant to find and score jobs, tailor CVs and write letters; the assistant connected, for anything AI; the
 * browser extension, to save postings in one click; the mailbox, for recruiters' replies to update the jobs; the
 * watchlist, for the jobs of the companies it would work for. app.js opens it on its own while a step is left, and the
 * profile menu whenever (see setup.js).
 */
@PageController
@RequiredArgsConstructor
class SetupGuideController {

	/** How many of the profile's latest requests tell whether its assistant ever did one. */
	static final int REQUESTS_LOOKED_AT = 50;

	private final CvService cvs;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final PromptCards promptCards;

	private final InboxService inbox;

	private final WatchlistService watchlist;

	@GetMapping(ControllerConstants.SETUP)
	String guide(Model model) {
		User profile = requireProfile(model);
		long userId = profile.id();
		Cv cv = this.cvs.find(userId);
		List<Step> steps = List.of(new Step("cv", "ph-identification-card", cv.file() != null || cv.details().isFilledIn(),
				ControllerConstants.CV),
				new Step("assistant", "ph-sparkle", assistantWorks(userId), ControllerConstants.SETTINGS + "#assistant"),
				// The site icons come from the extension alone, with the postings it saves
				new Step("extension", "ph-puzzle-piece", !this.applications.findSourcesWithIcon(userId).isEmpty(),
						ControllerConstants.SETTINGS + "#extension"),
				new Step("mailbox", "ph-envelope-simple", this.inbox.findAccount(userId).isPresent(), ControllerConstants.INBOX),
				new Step("watchlist", "ph-binoculars", !this.watchlist.findCompanies(userId).isEmpty(),
						ControllerConstants.WATCHLIST));
		model.addAttribute(ModelAttributes.SETUP_STEPS, steps);
		model.addAttribute(ModelAttributes.SETUP_DONE, steps.stream().filter(Step::done).count());
		return Views.SETUP_GUIDE_FRAGMENT;
	}

	/**
	 * Whether an assistant has JobWarden's MCP server on this computer, or did one of the profile's requests: JobWarden
	 * in Docker can't read the assistants' configuration, but sees what they did.
	 */
	private boolean assistantWorks(long userId) {
		return Arrays.stream(Assistant.values()).anyMatch(assistant -> this.promptCards.connection(assistant).state() == State.CONNECTED)
				|| this.discovery.findRequests(userId, REQUESTS_LOOKED_AT)
					.stream()
					.anyMatch(request -> request.status() == AiRequestStatus.DONE);
	}

	/**
	 * One thing to set up.
	 *
	 * @param key names its texts: setup.{key}, setup.{key}.unlocks
	 * @param icon its Phosphor icon, while it isn't done
	 * @param done whether the profile has it
	 * @param href where it's set up
	 */
	record Step(String key, String icon, boolean done, String href) {
	}

}
