package com.jobwarden.web.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchlistCheck;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.service.WatchlistService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
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
 * The watchlist: the companies whose job boards on Greenhouse, Lever and Ashby JobWarden reads itself every few hours,
 * with no Claude tokens, adding the postings that match the saved searches to the AI Assistant page. Watching some by
 * their links, several at once, or those the profile's jobs suggest; Check now; and removing one. Each change answers
 * with the page's watchlist part again, for HTMX to swap.
 */
@Controller
@RequestMapping(ControllerConstants.WATCHLIST)
@CustomLog
@RequiredArgsConstructor
class WatchlistController extends BaseController {

	/** How many companies the profile's jobs suggest. */
	static final int SUGGESTIONS = 8;

	/** How many links one paste watches: each is a board read. */
	static final int MAX_LINKS = 20;

	private static final Pattern BETWEEN_LINKS = Pattern.compile("[\\s,;]+");

	private final WatchlistService watchlist;

	private final DiscoveryService discovery;

	private final MessageSource messages;

	@GetMapping
	String page(Model model) {
		addWatchlist(model, requireProfile(model).id());
		return Views.WATCHLIST;
	}

	/**
	 * Called by HTMX with one link or more, between spaces or on lines of their own: to companies' boards, or to their
	 * jobs. Each board is read at once, and what matched goes to the AI Assistant page. The links that can't be
	 * watched stay in the field, each with why.
	 *
	 * @param name what to call the company, from a suggestion; for a single link only
	 */
	@PostMapping
	String watch(@RequestParam(defaultValue = "") String links, @RequestParam(required = false) @Nullable String name,
			Model model, Locale locale) {
		long userId = requireProfile(model).id();
		List<String> pasted = Arrays.stream(BETWEEN_LINKS.split(links.strip()))
			.filter(link -> !link.isEmpty())
			.distinct()
			.limit(MAX_LINKS)
			.toList();
		List<String> added = new ArrayList<>();
		List<String> already = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		int newJobs = 0;
		for (String link : pasted) {
			try {
				WatchResult watched = this.watchlist.watch(userId, link, (pasted.size() == 1) ? name : null);
				(watched.added() ? added : already).add(watched.company().name());
				newJobs += watched.newJobs();
			} catch (BusinessRuleException refused) {
				log.debug("Company not watched for profile {}: {}", userId, refused.getMessage());
				errors.add(this.messages.getMessage("watchlist.failedLink", new Object[] { link, message(refused) },
						locale));
				failed.add(link);
			}
		}
		if (pasted.isEmpty()) {
			errors.add(this.messages.getMessage(WatchlistErrorCode.NOT_A_BOARD_LINK.getMessageKey(), null, locale));
		}
		List<String> said = new ArrayList<>();
		if (!added.isEmpty()) {
			said.add(this.messages.getMessage("watchlist.added", new Object[] { String.join(", ", added), newJobs },
					locale));
		}
		if (!already.isEmpty()) {
			said.add(this.messages.getMessage("watchlist.alreadyWatched", new Object[] { String.join(", ", already) },
					locale));
		}
		model.addAttribute(ModelAttributes.WATCHLIST_MESSAGES, said);
		model.addAttribute(ModelAttributes.WATCHLIST_ERRORS, errors);
		model.addAttribute(ModelAttributes.WATCHLIST_LINKS, String.join("\n", failed));
		addWatchlist(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	@PostMapping("/{id}/delete")
	String unwatch(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.watchlist.unwatch(userId, id);
		addWatchlist(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	/**
	 * Called by HTMX: reads the boards now, rather than in the next few hours.
	 */
	@PostMapping("/check")
	String check(Model model, Locale locale) {
		long userId = requireProfile(model).id();
		WatchlistCheck checked = this.watchlist.check(userId);
		model.addAttribute(ModelAttributes.WATCHLIST_MESSAGES, List.of(this.messages.getMessage("watchlist.checkedNow",
				new Object[] { checked.companies(), checked.newJobs(), checked.failed() }, locale)));
		addWatchlist(model, userId);
		return Views.WATCHLIST_FRAGMENT;
	}

	/** The companies, those the profile's jobs suggest, and how many jobs wait on the AI Assistant page. */
	private void addWatchlist(Model model, long userId) {
		model.addAttribute(ModelAttributes.WATCHED_COMPANIES, this.watchlist.findCompanies(userId));
		model.addAttribute(ModelAttributes.WATCHLIST_SUGGESTIONS, this.watchlist.suggest(userId, SUGGESTIONS));
		model.addAttribute(ModelAttributes.WATCHLIST_HAS_SEARCHES, !this.discovery.findSearches(userId).isEmpty());
		model.addAttribute(ModelAttributes.TO_REVIEW_ALL, this.discovery.countToReview(userId, null));
	}

}
