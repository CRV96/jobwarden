package com.jobwarden.web.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.search.Recency;
import com.jobwarden.search.WorkType;
import com.jobwarden.web.dto.AiRequestForm;
import com.jobwarden.web.dto.SearchFilters;
import com.jobwarden.web.view.ClaudePrompts;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The AI Assistant page: what the profile asks Claude to do (its presets, as requests Claude picks up through the MCP
 * server), the jobs Claude found, one card at a time, to save, apply to or discard, and the searches it finds them for,
 * which the search page saves here.
 */
@Controller
@RequestMapping(ControllerConstants.DISCOVER)
@CustomLog
@RequiredArgsConstructor
class DiscoverController extends BaseController {

	/** How many of the latest decisions the page lists, to undo one. */
	private static final int RECENT = 8;

	/** How many of the latest requests the page lists, and of those, how many list the jobs found for them. */
	private static final int REQUESTS = 6;

	private static final int REQUESTS_WITH_JOBS = 3;

	private final DiscoveryService discovery;

	private final CvService cvs;

	/**
	 * @param search only the jobs found for this saved search
	 */
	@GetMapping
	String discover(@RequestParam(required = false) @Nullable Long search, Model model) {
		long userId = requireProfile(model).id();
		List<SavedSearch> searches = addReview(model, userId, search);
		model.addAttribute(ModelAttributes.SEARCH_URLS, searches.stream()
			.collect(Collectors.toMap(SavedSearch::id, saved -> SearchFilters.url(saved.criteria()))));
		Cv cv = this.cvs.find(userId);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.WORK_TYPES, WorkType.values());
		model.addAttribute(ModelAttributes.RECENCIES, Recency.values());
		addRequests(model, userId);
		return Views.DISCOVER;
	}

	/**
	 * Called by HTMX from a preset's button, with what to look for: the request waits for Claude, and the answer is
	 * the list of requests with its prompt.
	 */
	@PostMapping("/requests")
	String ask(@Valid AiRequestForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			log.debug("Request to Claude not made, fields with errors: {}", fieldErrors(result).keySet());
			model.addAttribute(ModelAttributes.AI_ERROR, errorMessage(result));
		} else {
			this.discovery.createRequest(userId, form.kind(), form.toRequirements());
		}
		addRequests(model, userId);
		return Views.DISCOVER_REQUESTS_FRAGMENT;
	}

	/**
	 * Called by HTMX every few seconds while a request is open. When the jobs to review changed since the page last
	 * showed them ({@code seen}), it tells the card to load again.
	 */
	@GetMapping("/requests")
	String requests(@RequestParam(required = false) @Nullable Long seen, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		addRequests(model, userId);
		if (seen != null && seen != this.discovery.countToReview(userId, null)) {
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.FOUND_JOBS_CHANGED);
		}
		return Views.DISCOVER_REQUESTS_FRAGMENT;
	}

	@PostMapping("/requests/{id}/cancel")
	String cancel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.cancelRequest(userId, id);
		addRequests(model, userId);
		return Views.DISCOVER_REQUESTS_FRAGMENT;
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
	 * The latest requests, the jobs found for the latest of them, and the prompts that hand them to Claude Code.
	 */
	private void addRequests(Model model, long userId) {
		List<AiRequest> requests = this.discovery.findRequests(userId, REQUESTS);
		model.addAttribute(ModelAttributes.AI_REQUESTS, requests);
		model.addAttribute(ModelAttributes.AI_REQUEST_JOBS, requests.stream()
			.filter(request -> request.foundJobs() > 0)
			.limit(REQUESTS_WITH_JOBS)
			.collect(Collectors.toMap(AiRequest::id, request -> this.discovery.findFoundJobsOfRequest(userId, request.id()))));
		model.addAttribute(ModelAttributes.AI_PROMPTS, requests.stream()
			.filter(request -> request.status() == AiRequestStatus.WAITING)
			.collect(Collectors.toMap(AiRequest::id, request -> ClaudePrompts.forRequest(request.id(), userId))));
		model.addAttribute(ModelAttributes.AI_LOOP_PROMPT, ClaudePrompts.loop(userId));
		model.addAttribute(ModelAttributes.AI_OPEN, requests.stream().anyMatch(request -> request.status().isOpen()));
		model.addAttribute(ModelAttributes.TO_REVIEW_ALL, this.discovery.countToReview(userId, null));
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
