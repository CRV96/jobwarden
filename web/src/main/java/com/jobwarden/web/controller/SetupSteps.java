package com.jobwarden.web.controller;

import java.util.Arrays;
import java.util.List;
import java.util.function.LongPredicate;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.assistant.AssistantConnections.State;
import com.jobwarden.web.assistant.PromptCards;
import com.jobwarden.web.constants.ControllerConstants;

import org.springframework.stereotype.Component;

/**
 * What to set up for each part of JobWarden to work, in the order to do it, and which of it a profile has done: its CV,
 * for the AI Assistant to find and score jobs, tailor CVs and write letters; its name and contact details, at the top
 * of every CV tailored; the assistant connected, for anything AI; the browser extension, to save postings in one
 * click; the mailbox, for recruiters' replies to update the jobs; the watchlist, for the jobs of the companies it would
 * work for. The setup guide shows them (SetupGuideController); the profile menu offers the guide while one is left
 * (LayoutAdvice).
 */
@Component
class SetupSteps {

	/** How many of the profile's latest requests tell whether its assistant ever did one. */
	static final int REQUESTS_LOOKED_AT = 50;

	private final CvService cvs;

	private final DiscoveryService discovery;

	private final PromptCards promptCards;

	/** Each step, with what tells whether a profile has done it: in the order to do them. */
	private final List<Kind> kinds;

	SetupSteps(CvService cvs, ApplicationService applications, DiscoveryService discovery, PromptCards promptCards,
			InboxService inbox, WatchlistService watchlist) {
		this.cvs = cvs;
		this.discovery = discovery;
		this.promptCards = promptCards;
		this.kinds = List.of(
				new Kind("cv", "ph-identification-card", ControllerConstants.CV, userId -> {
					Cv cv = this.cvs.find(userId);
					return cv.file() != null || cv.details().isFilledIn();
				}),
				new Kind("contacts", "ph-address-book", ControllerConstants.CV + "#contacts",
						userId -> this.cvs.find(userId).details().hasContactDetails()),
				new Kind("assistant", "ph-sparkle", ControllerConstants.SETTINGS + "#assistant", this::assistantWorks),
				// The site icons come from the extension alone, with the postings it saves
				new Kind("extension", "ph-puzzle-piece", ControllerConstants.SETTINGS + "#extension",
						userId -> !applications.findSourcesWithIcon(userId).isEmpty()),
				new Kind("mailbox", "ph-envelope-simple", ControllerConstants.INBOX,
						userId -> inbox.findAccount(userId).isPresent()),
				new Kind("watchlist", "ph-binoculars", ControllerConstants.WATCHLIST,
						userId -> !watchlist.findCompanies(userId).isEmpty()));
	}

	/**
	 * @return every step, with whether the profile has done it
	 */
	List<Step> of(long userId) {
		return this.kinds.stream().map(kind -> new Step(kind.key(), kind.icon(), kind.done().test(userId), kind.href())).toList();
	}

	/**
	 * @return whether the profile has done every step: it stops at the first that isn't
	 */
	boolean complete(long userId) {
		return this.kinds.stream().allMatch(kind -> kind.done().test(userId));
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

	/**
	 * @param done tells whether a profile, by its id, has done it
	 */
	private record Kind(String key, String icon, String href, LongPredicate done) {
	}

}
