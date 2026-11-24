package com.jobwarden.web.controller;

import java.util.EnumMap;
import java.util.Map;

import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.HtmxHeaders;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.enums.JobsView;
import com.jobwarden.web.view.JobImages;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import static com.jobwarden.web.controller.Pages.redirectTo;
import static com.jobwarden.web.controller.Pages.rememberJobsView;
import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * The Jobs page as a board: every application as a card, in a column per status, newest first (the list is the other
 * view, {@link ApplicationController#list}). A column shows its first cards, and loads the next ones as it's scrolled
 * to its end: a profile with thousands of applications gets a page as quick as one with ten. The search above narrows
 * every column to the jobs whose title or company has the words.
 */
@PageController
@RequiredArgsConstructor
class PipelineController {

	/** How many cards a column shows at first, and loads at a time after. */
	static final int PAGE_SIZE = 40;

	private final ApplicationService applications;

	private final JobFitService fits;

	private final JobImages jobImages;

	/**
	 * @param q only the jobs whose title or company has these words; all of them when blank
	 */
	@GetMapping(ControllerConstants.JOB_BOARD)
	String board(@RequestParam(required = false) @Nullable String q, Model model,
			@RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		if (htmx == null) {
			rememberJobsView(response, JobsView.BOARD);
		}
		String search = searchOf(q);
		Map<ApplicationStatus, Page<ApplicationSummary>> columns = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus status : ApplicationStatus.values()) {
			columns.put(status, page(userId, status, search, 0));
		}
		model.addAttribute(ModelAttributes.PIPELINE_COLUMNS, columns);
		model.addAttribute(ModelAttributes.FIT_SCORES, this.fits.findScores(userId, columns.values()
			.stream()
			.flatMap(page -> page.getContent().stream())
			.map(ApplicationSummary::id)
			.toList()));
		model.addAttribute(ModelAttributes.PIPELINE_SEARCH, search);
		this.jobImages.addImages(model, userId);
		// For the add-job dialog
		model.addAttribute(ModelAttributes.STATUSES, ApplicationStatus.values());
		model.addAttribute(ModelAttributes.REJECTION_REASONS, RejectionReason.values());
		return Views.PIPELINE;
	}

	/**
	 * The next cards of a column, which HTMX adds at its end as it's scrolled to there.
	 *
	 * @param page from 0, the first one the board shows
	 */
	@GetMapping(ControllerConstants.JOB_BOARD + "/column")
	String column(@RequestParam ApplicationStatus status, @RequestParam int page,
			@RequestParam(required = false) @Nullable String q, Model model) {
		long userId = requireProfile(model).id();
		String search = searchOf(q);
		Page<ApplicationSummary> cards = page(userId, status, search, Math.max(page, 0));
		model.addAttribute(ModelAttributes.PIPELINE_CARDS, cards);
		model.addAttribute(ModelAttributes.FIT_SCORES,
				this.fits.findScores(userId, cards.getContent().stream().map(ApplicationSummary::id).toList()));
		model.addAttribute(ModelAttributes.PIPELINE_STATUS, status);
		model.addAttribute(ModelAttributes.PIPELINE_SEARCH, search);
		this.jobImages.addImages(model, userId);
		return Views.PIPELINE_CARDS_FRAGMENT;
	}

	/**
	 * The pipeline's old address, from before it was a view of the Jobs page: a bookmark of it opens the board.
	 */
	@GetMapping(ControllerConstants.PIPELINE)
	String pipeline() {
		return redirectTo(ControllerConstants.JOB_BOARD);
	}

	private static @Nullable String searchOf(@Nullable String q) {
		return (q == null || q.isBlank()) ? null : q.strip();
	}

	private Page<ApplicationSummary> page(long userId, ApplicationStatus status, @Nullable String search, int page) {
		return this.applications.find(userId, ApplicationQuery.builder()
			.search(search)
			.status(status)
			.sort(ApplicationSort.ADDED)
			.direction(Sort.Direction.DESC)
			.page(page)
			.size(PAGE_SIZE)
			.build());
	}

}
