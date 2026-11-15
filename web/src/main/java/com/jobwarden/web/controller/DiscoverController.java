package com.jobwarden.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.dto.SearchCount;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.TidyResult;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.search.Recency;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.AiRequestForm;
import com.jobwarden.web.dto.SearchFilters;
import com.jobwarden.web.view.AssistantPrompts;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

	/**
	 * Tidying up: after how many days with no news an application at Applied moves to No response, and how long after
	 * applying to follow up on a newer one. The same as the MCP server's tidy_applications.
	 */
	static final int NO_RESPONSE_AFTER_DAYS = 30;

	static final int FOLLOW_UP_AFTER_DAYS = 7;

	private final DiscoveryService discovery;

	private final CvService cvs;

	private final ApplicationService applications;

	private final MessageSource messages;

	/** Where Claude's loop asks whether requests wait (ClaudeFilesController). */
	@Value("${jobwarden.claude.requests-path:/claude/requests}")
	private final String requestsPath;

	/** Where the MCP server is, which the loop gives the assistant itself. */
	@Value("${jobwarden.mcp.path:/mcp}")
	private final String mcpPath;

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
	String ask(@Valid AiRequestForm form, BindingResult result, Model model, Locale locale) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			log.debug("Request not made, fields with errors: {}", fieldErrors(result).keySet());
			model.addAttribute(ModelAttributes.AI_ERROR, errorMessage(result));
		} else {
			AiRequest request = this.discovery.createRequest(userId, form.kind(), form.toRequirements());
			if (request.kind() == AiRequestKind.FOLLOW_UP) {
				tidyUp(userId, request, locale);
			}
		}
		addRequests(model, userId);
		return Views.DISCOVER_REQUESTS_FRAGMENT;
	}

	/**
	 * Tidying up the applications with no answer needs no AI, so JobWarden does it at once, and Claude spends no tokens
	 * on it: the request is done as it's made, with what it did as its summary.
	 */
	private void tidyUp(long userId, AiRequest request, Locale locale) {
		TidyResult tidied = this.applications.tidyUnanswered(userId, NO_RESPONSE_AFTER_DAYS, FOLLOW_UP_AFTER_DAYS,
				this.messages.getMessage("ai.followUp.note", null, locale));
		this.discovery.finishRequest(userId, request.id(), true, this.messages.getMessage("ai.followUp.done",
				new Object[] { tidied.movedToNoResponse().size(), tidied.followUpsSet().size(), tidied.waiting() }, locale));
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
	 * Called by HTMX from the search page, with its filters: keeps them under the name, in place of those it had. The
	 * answer says so, and brings the page's menu of saved filters along.
	 */
	@PostMapping("/searches")
	String saveSearch(@RequestParam MultiValueMap<String, String> parameters, @RequestParam String name, Model model) {
		long userId = requireProfile(model).id();
		SavedSearch saved = this.discovery.saveSearch(userId, name, SearchFilters.from(parameters).criteria());
		model.addAttribute(ModelAttributes.SAVED_SEARCH, saved);
		// The saved filters' menu shows it at once
		SearchController.addSavedFilters(model, this.discovery, userId);
		return Views.SEARCH_SAVED_FRAGMENT;
	}

	/**
	 * From the homepage: asks the assistant to find jobs with a saved search's filters, and goes to this page, where
	 * the request waits with what to paste, the apps to open and Run.
	 */
	@PostMapping("/searches/{id}/ask")
	String askWithSearch(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		SavedSearch search = this.discovery.findSearch(userId, id);
		this.discovery.createRequest(userId, AiRequestKind.FIND_JOBS, requirements(search));
		log.info("Profile {} asked the assistant to find jobs with saved search {}", userId, id);
		return redirectTo(ControllerConstants.DISCOVER);
	}

	/**
	 * A saved search's filters, as what to look for: the job boards and the kinds of contract it names go in the notes,
	 * which the assistant reads.
	 */
	static AiRequirements requirements(SavedSearch search) {
		SearchCriteria criteria = search.criteria();
		List<String> notes = new ArrayList<>();
		if (!criteria.sites().isEmpty()) {
			notes.add("On these job boards: " + String.join(", ", criteria.sites()) + ".");
		}
		if (!criteria.employmentTypes().isEmpty()) {
			notes.add("Employment: " + criteria.employmentTypes()
				.stream()
				.map(type -> type.name().toLowerCase(Locale.ROOT).replace('_', ' '))
				.collect(Collectors.joining(", ")) + ".");
		}
		return AiRequirements.builder()
			.titles(criteria.titles())
			.locations(criteria.locations())
			.skills(criteria.skills())
			.excludes(criteria.excludes())
			.workTypes(criteria.workTypes())
			.recency(criteria.recency())
			// Its id first, whatever its name says, which the request's steps pass to add_found_jobs: the jobs then show
			// under the search
			.notes(("(search_id " + search.id() + ") From the saved search " + search.name() + ". " + String.join(" ", notes))
				.strip())
			.build();
	}

	@PostMapping("/searches/{id}/delete")
	String deleteSearch(@PathVariable long id, Model model) {
		this.discovery.deleteSearch(requireProfile(model).id(), id);
		return redirectTo(ControllerConstants.DISCOVER);
	}

	/**
	 * The latest requests, the jobs found for the latest of them, and the prompts that hand them to the profile's AI
	 * assistant, Claude Code or Codex.
	 */
	private void addRequests(Model model, long userId) {
		List<AiRequest> requests = this.discovery.findRequests(userId, REQUESTS);
		model.addAttribute(ModelAttributes.AI_REQUESTS, requests);
		model.addAttribute(ModelAttributes.AI_REQUEST_JOBS, requests.stream()
			.filter(request -> request.foundJobs() > 0)
			.limit(REQUESTS_WITH_JOBS)
			.collect(Collectors.toMap(AiRequest::id, request -> this.discovery.findFoundJobsOfRequest(userId, request.id()))));
		// Auto-apply's is Claude Code's with Claude in Chrome, never run here; the others', the profile's assistant
		model.addAttribute(ModelAttributes.AI_PROMPTS, requests.stream()
			.filter(request -> request.status() == AiRequestStatus.WAITING)
			.collect(Collectors.toMap(AiRequest::id, request -> (request.kind() == AiRequestKind.AUTO_APPLY)
					? connected(AssistantPrompts.forApplying(request.id(), userId), requireProfile(model))
					: prompt(model, request.id()))));
		model.addAttribute(ModelAttributes.AI_LOOP_PROMPT, AssistantPrompts.loop(requireProfile(model).assistant(), userId,
				ServletUriComponentsBuilder.fromCurrentContextPath().path(this.requestsPath).toUriString(),
				ServletUriComponentsBuilder.fromCurrentContextPath().path(this.mcpPath).toUriString()));
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
