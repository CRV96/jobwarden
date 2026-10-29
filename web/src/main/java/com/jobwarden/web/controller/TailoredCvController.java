package com.jobwarden.web.controller;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.ClaudePrompts;
import com.jobwarden.web.view.CvPdf;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * A job's AI-proof CV: the profile's CV, rewritten by Claude for the job. The job's page asks Claude for it with a
 * request, which Claude Code does through the MCP server (save_tailored_cv); its card shows the CV once it's saved,
 * previewed like the CV page's and downloaded as a PDF.
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/cv")
@CustomLog
@RequiredArgsConstructor
class TailoredCvController extends BaseController {

	private final ApplicationService applications;

	private final CvService cvs;

	private final DiscoveryService discovery;

	private final CvPdf pdf;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping("/card")
	String card(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showCard(model, userId, id);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.TAILOR_CV, id);
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
		this.cvs.deleteTailored(userId, id);
		return showCard(model, userId, id);
	}

	/**
	 * The CV for the job, as a page of its own, like the CV page's preview.
	 */
	@GetMapping
	String preview(@PathVariable long id, Model model) {
		model.addAttribute(ModelAttributes.CV_DETAILS, requireTailored(requireProfile(model).id(), id).details());
		return Views.CV_DOCUMENT;
	}

	/**
	 * The PDF, named for the person and the company: "CV Ana Pop - Acme.pdf".
	 */
	@GetMapping("/pdf")
	ResponseEntity<byte[]> downloadPdf(@PathVariable long id, Model model, Locale locale) {
		long userId = requireProfile(model).id();
		ApplicationDetails application = requireApplication(userId, id);
		CvDetails details = requireTailored(userId, id).details();
		byte[] document = this.pdf.render(Map.of(ModelAttributes.CV_DETAILS, details), locale);
		return attachment(document, MediaType.APPLICATION_PDF, Attachments.cvName(details.fullName(), application.company()));
	}

	private String showCard(Model model, long userId, long applicationId) {
		Cv cv = this.cvs.find(userId);
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.CV_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.TAILORED_CV, this.cvs.findTailored(userId, applicationId).orElse(null));
		model.addAttribute(ModelAttributes.CV_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.CV_PROMPT,
				request.map(asked -> ClaudePrompts.forRequest(asked.id(), userId)).orElse(null));
		return Views.TAILORED_CV_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.TAILOR_CV, applicationId);
	}

	private ApplicationDetails requireApplication(long userId, long applicationId) {
		return this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

	private TailoredCv requireTailored(long userId, long applicationId) {
		return this.cvs.findTailored(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
