package com.jobwarden.web.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.ApplyAnswersForm;
import com.jobwarden.web.view.ClaudePrompts;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Auto-apply: Claude applies to the profile's Saved jobs in its browser, with the CV and the answers saved here. The
 * page queues jobs and asks Claude with a request, which Claude Code does through the MCP server (list_apply_queue,
 * report_apply_result); it shows how each job went, and takes the answers Claude asked for.
 */
@Controller
@RequestMapping(ControllerConstants.APPLY)
@CustomLog
@RequiredArgsConstructor
class ApplyController extends BaseController {

	/** How many queued jobs the page shows, the latest first. */
	static final int ITEMS_SHOWN = 50;

	private final AutoApplyService autoApply;

	private final ApplicationService applications;

	private final DiscoveryService discovery;

	private final CvService cvs;

	@GetMapping
	String page(Model model) {
		long userId = requireProfile(model).id();
		return showPage(model, userId, this.autoApply.findAnswers(userId), Map.of());
	}

	/**
	 * Saves the answers. Invalid ones show the page again, as they were typed, with the errors.
	 */
	@PostMapping("/answers")
	String saveAnswers(@Valid ApplyAnswersForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			return showPage(model, userId, form.toAnswers(), fieldErrors(result));
		}
		this.autoApply.saveAnswers(userId, form.toAnswers());
		return redirectTo(ControllerConstants.APPLY + "?saved");
	}

	/**
	 * Queues the ticked jobs and asks Claude to apply to them: the page then shows the prompt for Claude Code.
	 */
	@PostMapping("/queue")
	String queue(@RequestParam(required = false) @Nullable List<Long> ids, @RequestParam ApplyMode mode, Model model,
			RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		if (ids == null || ids.isEmpty() || this.autoApply.queue(userId, ids, mode).isEmpty()) {
			redirect.addFlashAttribute(ModelAttributes.APPLY_ERROR, true);
			return redirectTo(ControllerConstants.APPLY);
		}
		askClaude(userId);
		return redirectTo(ControllerConstants.APPLY);
	}

	/**
	 * The run of Claude again, which the page asks for every few seconds while it's open. Once it's done, the page
	 * reloads, with how each job went.
	 *
	 * @param waiting whether the page showed it open
	 */
	@GetMapping("/run")
	String run(@RequestParam(defaultValue = "false") boolean waiting, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		AiRequest run = addRun(model, userId);
		if (waiting && run != null && !run.status().isOpen()) {
			response.setHeader(HtmxHeaders.REFRESH, "true");
		}
		return Views.APPLY_RUN_FRAGMENT;
	}

	@PostMapping("/runs/{id}/cancel")
	String cancelRun(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.cancelRequest(userId, id);
		addRun(model, userId);
		return Views.APPLY_RUN_FRAGMENT;
	}

	/**
	 * The profile sent the form Claude filled in: the job moves to Applied.
	 */
	@PostMapping("/items/{id}/sent")
	String sent(@PathVariable long id, Model model) {
		this.autoApply.markSent(requireProfile(model).id(), id);
		return redirectTo(ControllerConstants.APPLY);
	}

	@PostMapping("/items/{id}/cancel")
	String cancel(@PathVariable long id, Model model) {
		this.autoApply.cancel(requireProfile(model).id(), id);
		return redirectTo(ControllerConstants.APPLY);
	}

	/**
	 * Queues the job again, and asks Claude to go on unless it's already asked.
	 */
	@PostMapping("/items/{id}/retry")
	String retry(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.autoApply.retry(userId, id);
		askClaudeUnlessAsked(userId);
		return redirectTo(ControllerConstants.APPLY);
	}

	/**
	 * Answers the question the form asked, among the profile's own answers for the next forms too, and queues the job
	 * again.
	 */
	@PostMapping("/items/{id}/answer")
	String answer(@PathVariable long id, @RequestParam String answer, Model model) {
		long userId = requireProfile(model).id();
		this.autoApply.answer(userId, id, answer);
		askClaudeUnlessAsked(userId);
		return redirectTo(ControllerConstants.APPLY);
	}

	private String showPage(Model model, long userId, ApplicationAnswers answers, Map<String, String> fieldErrors) {
		List<ApplyItem> items = this.autoApply.findItems(userId, ITEMS_SHOWN);
		Set<Long> waiting = items.stream()
			.filter(item -> ApplyStatus.WAITING.contains(item.status()))
			.map(ApplyItem::applicationId)
			.collect(Collectors.toSet());
		Cv cv = this.cvs.find(userId);
		model.addAttribute(ModelAttributes.APPLY_ANSWERS, answers);
		model.addAttribute(ModelAttributes.FIELD_ERRORS, fieldErrors);
		model.addAttribute(ModelAttributes.APPLY_ITEMS, items);
		model.addAttribute(ModelAttributes.APPLY_JOBS, this.applications.findAll(userId, ApplicationStatus.SAVED)
			.stream()
			.filter(job -> isWebAddress(job.link()) && !waiting.contains(job.id()))
			.toList());
		model.addAttribute(ModelAttributes.CV_DETAILS, cv.details());
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.COVER_LETTERS, CoverLetter.values());
		addRun(model, userId);
		return Views.APPLY;
	}

	/**
	 * The latest run of Claude on the queue, with what to paste into Claude Code while it waits.
	 *
	 * @return it, or {@code null} if Claude was never asked
	 */
	private @Nullable AiRequest addRun(Model model, long userId) {
		Optional<AiRequest> latest = latestRun(userId);
		model.addAttribute(ModelAttributes.APPLY_RUN, latest.orElse(null));
		model.addAttribute(ModelAttributes.APPLY_PROMPT,
				latest.map(run -> ClaudePrompts.forRequest(run.id(), userId)).orElse(null));
		return latest.orElse(null);
	}

	private Optional<AiRequest> latestRun(long userId) {
		return this.discovery.findRequests(userId, ITEMS_SHOWN)
			.stream()
			.filter(request -> request.kind() == AiRequestKind.AUTO_APPLY)
			.findFirst();
	}

	private void askClaude(long userId) {
		this.discovery.createRequest(userId, AiRequestKind.AUTO_APPLY,
				AiRequirements.builder().maxJobs(AiRequirements.MAX_JOBS).build());
	}

	private void askClaudeUnlessAsked(long userId) {
		if (latestRun(userId).filter(run -> run.status().isOpen()).isEmpty()) {
			askClaude(userId);
		}
	}

	private static boolean isWebAddress(@Nullable String link) {
		return link != null && (link.startsWith("https://") || link.startsWith("http://"));
	}

}
