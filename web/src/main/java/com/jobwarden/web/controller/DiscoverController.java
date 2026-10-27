package com.jobwarden.web.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.SearchFilters;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The jobs Claude found for the profile, one card at a time, to save, apply to or discard; and the searches it finds
 * them for, which the search page saves here.
 */
@Controller
@RequestMapping(ControllerConstants.DISCOVER)
@RequiredArgsConstructor
class DiscoverController extends BaseController {

	/** How many of the latest decisions the page lists, to undo one. */
	private static final int RECENT = 8;

	private final DiscoveryService discovery;

	/**
	 * @param search only the jobs found for this saved search
	 */
	@GetMapping
	String discover(@RequestParam(required = false) @Nullable Long search, Model model) {
		long userId = requireProfile(model).id();
		List<SavedSearch> searches = addReview(model, userId, search);
		model.addAttribute(ModelAttributes.SEARCH_URLS, searches.stream()
			.collect(Collectors.toMap(SavedSearch::id, saved -> SearchFilters.url(saved.criteria()))));
		return Views.DISCOVER;
	}

	/**
	 * Called by HTMX from the card: answers with the next card, and the counts and the latest decisions that changed
	 * with it. Applying opens the posting too, in a tab of its own (app.js), as the decision is sent.
	 */
	@PostMapping("/{id}/decision")
	String decide(@PathVariable long id, @RequestParam Decision decision,
			@RequestParam(required = false) @Nullable Long search,
			@RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.decide(userId, id, decision);
		if (htmx == null) {
			return redirectTo(ControllerConstants.DISCOVER + ((search != null) ? "?search=" + search : ""));
		}
		addReview(model, userId, search);
		return Views.DISCOVER_REVIEW_FRAGMENT;
	}

	/**
	 * Called by HTMX from the search page, with its filters: keeps them under the name.
	 */
	@PostMapping("/searches")
	String saveSearch(@RequestParam MultiValueMap<String, String> parameters, @RequestParam String name, Model model) {
		long userId = requireProfile(model).id();
		SavedSearch saved = this.discovery.saveSearch(userId, name, SearchFilters.from(parameters).criteria());
		model.addAttribute(ModelAttributes.SAVED_SEARCH, saved);
		return Views.SEARCH_SAVED_FRAGMENT;
	}

	@PostMapping("/searches/{id}/delete")
	String deleteSearch(@PathVariable long id, Model model) {
		this.discovery.deleteSearch(requireProfile(model).id(), id);
		return redirectTo(ControllerConstants.DISCOVER);
	}

	/**
	 * The card to review, and the counts and the latest decisions the page shows with it.
	 *
	 * @return the profile's saved searches, which it added too
	 */
	private List<SavedSearch> addReview(Model model, long userId, @Nullable Long search) {
		List<SavedSearch> searches = this.discovery.findSearches(userId);
		model.addAttribute(ModelAttributes.SAVED_SEARCHES, searches);
		model.addAttribute(ModelAttributes.SELECTED_SEARCH, search);
		model.addAttribute(ModelAttributes.TO_REVIEW_ALL, this.discovery.countToReview(userId, null));
		model.addAttribute(ModelAttributes.FOUND_JOB, this.discovery.nextToReview(userId, search).orElse(null));
		model.addAttribute(ModelAttributes.TO_REVIEW, this.discovery.countToReview(userId, search));
		Map<Long, Long> counts = this.discovery.countToReviewBySearch(userId)
			.stream()
			.collect(Collectors.toMap(count -> (count.searchId() != null) ? count.searchId() : 0L, SearchCount::count));
		model.addAttribute(ModelAttributes.SEARCH_COUNTS, counts);
		model.addAttribute(ModelAttributes.RECENT_DECISIONS, this.discovery.findDecided(userId, RECENT));
		return searches;
	}

}
