package com.jobwarden.web.controller;

import java.util.Optional;

import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.ClaudePrompts;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * How well the profile fits a job, to apply better to fewer: the score Claude's reading of the posting's requirements
 * against the CV comes to, and what JobWarden notices about the posting on its own (it rules out sponsorship the
 * profile needs, it was posted again, it's been up for long). The job's page asks Claude for the score with a request,
 * which Claude Code does through the MCP server (save_job_fit).
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/fit")
@RequiredArgsConstructor
class JobFitController extends BaseController {

	private final JobFitService fits;

	private final DiscoveryService discovery;

	private final AutoApplyService autoApply;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping
	String card(@PathVariable long id, Model model) {
		return showCard(model, requireProfile(model).id(), id);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.SCORE_JOB, id);
		return showCard(model, userId, id);
	}

	@PostMapping("/cancel")
	String cancel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		latestRequest(userId, id).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		return showCard(model, userId, id);
	}

	@PostMapping("/delete")
	String delete(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.fits.delete(userId, id);
		return showCard(model, userId, id);
	}

	private String showCard(Model model, long userId, long applicationId) {
		model.addAttribute(ModelAttributes.FIT_FLAGS,
				this.fits.findFlags(userId, applicationId, this.autoApply.findAnswers(userId).needsSponsorship()));
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.FIT_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.JOB_FIT, this.fits.find(userId, applicationId).orElse(null));
		model.addAttribute(ModelAttributes.FIT_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.FIT_PROMPT,
				request.map(asked -> ClaudePrompts.forRequest(asked.id(), userId)).orElse(null));
		return Views.JOB_FIT_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.SCORE_JOB, applicationId);
	}

}
