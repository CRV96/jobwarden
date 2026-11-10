package com.jobwarden.web.controller;

import java.util.Locale;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The AI Assistant page's watchlist card: the companies whose job boards on Greenhouse, Lever and Ashby JobWarden
 * reads itself every few hours, with no Claude tokens, to add the postings that match the saved searches to the page;
 * adding one by a link, those the profile's jobs suggest, Check now, and removing one. The page loads the card after
 * itself, and each action answers with the card again; when jobs were added, the card to review loads again too.
 */
@Controller
@RequestMapping(ControllerConstants.WATCHLIST)
@CustomLog
@RequiredArgsConstructor
class WatchlistController extends BaseController {

	/** How many companies the profile's jobs suggest. */
	static final int SUGGESTIONS = 6;

	private static final int LINK_MAX_LENGTH = 500;

	private final WatchlistService watchlist;

	private final DiscoveryService discovery;

	private final MessageSource messages;

	@GetMapping
	String card(Model model) {
		addCard(model, requireProfile(model).id());
		return Views.WATCHLIST_FRAGMENT;
	}

	/**
	 * Called by HTMX with a link to the company's board, or to one of its jobs: the board is read at once, and what
	 * matched goes to the page.
	 *
	 * @param name what to call the company, from a suggestion
	 */
	@PostMapping
	String watch(@RequestParam(defaultValue = "") String link, @RequestParam(required = false) @Nullable String name,
			Model model, Locale locale, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		String typed = (link.length() <= LINK_MAX_LENGTH) ? link.strip() : link.substring(0, LINK_MAX_LENGTH);
		try {
			WatchResult watched = this.watchlist.watch(userId, typed, name);
			String said = watched.added() ? "watchlist.added" : "watchlist.alreadyWatched";
			model.addAttribute(ModelAttributes.WATCHLIST_MESSAGE,
					this.messages.getMessage(said, new Object[] { watched.company().name(), watched.newJobs() }, locale));
			foundJobsChanged(response, watched.newJobs());
		} catch (BusinessRuleException refused) {
			log.debug("Company not watched for profile {}: {}", userId, refused.getMessage());
			model.addAttribute(ModelAttributes.WATCHLIST_ERROR, message(refused));
			model.addAttribute(ModelAttributes.WATCHLIST_LINK, typed);
		}
		addCard(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	@PostMapping("/{id}/delete")
	String unwatch(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.watchlist.unwatch(userId, id);
		addCard(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	/**
	 * Called by HTMX: reads the boards now, rather than in the next few hours.
	 */
	@PostMapping("/check")
	String check(Model model, Locale locale, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		WatchlistCheck checked = this.watchlist.check(userId);
		model.addAttribute(ModelAttributes.WATCHLIST_MESSAGE, this.messages.getMessage("watchlist.checkedNow",
				new Object[] { checked.companies(), checked.newJobs(), checked.failed() }, locale));
		foundJobsChanged(response, checked.newJobs());
		addCard(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	/** The card to review on the page loads again, with the jobs added. */
	private static void foundJobsChanged(HttpServletResponse response, int newJobs) {
		if (newJobs > 0) {
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.FOUND_JOBS_CHANGED);
		}
	}

	private void addCard(Model model, long userId) {
		model.addAttribute(ModelAttributes.WATCHED_COMPANIES, this.watchlist.findCompanies(userId));
		model.addAttribute(ModelAttributes.WATCHLIST_SUGGESTIONS, this.watchlist.suggest(userId, SUGGESTIONS));
		model.addAttribute(ModelAttributes.WATCHLIST_HAS_SEARCHES, !this.discovery.findSearches(userId).isEmpty());
	}

}
