package com.jobwarden.web.controller;

import java.util.Locale;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.LetterPdf;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A job's cover letter: written by Claude from the CV and the posting, changed by the profile on the job's page, and
 * downloaded as a PDF with the CV page's name and contact details. The page asks Claude for it with a request, which
 * Claude Code does through the MCP server (save_cover_letter); the card shows the letter once it's saved. A letter can
 * also be written there without Claude, starting from the profile's base letter (the Cover letters page,
 * LettersController) when it has one. The same card shows on that page, beside its list of jobs.
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/letter")
@CustomLog
@RequiredArgsConstructor
class CoverLetterController extends BaseController {

	private final ApplicationService applications;

	private final CoverLetterService letters;

	private final CvService cvs;

	private final DiscoveryService discovery;

	private final LetterPdf pdf;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping("/card")
	String card(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showCard(model, userId, id, false);
	}

	/**
	 * The card with the letter in a field to change, or to write one without Claude.
	 */
	@GetMapping("/edit")
	String edit(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showCard(model, userId, id, true);
	}

	/**
	 * Keeps the letter as changed; when it can't be kept (empty, too long), the field stays open with what was typed,
	 * saying why. A change kept takes back the request for Claude to write it, if one is open: the profile's own letter
	 * wins, and save_cover_letter refuses Claude's, should it come after all (LetterTools).
	 */
	@PostMapping
	String save(@PathVariable long id, @RequestParam(defaultValue = "") String body, Model model,
			HttpServletResponse response) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		try {
			this.letters.changeLetter(userId, id, body);
		} catch (BusinessRuleException refused) {
			log.debug("Cover letter of application {} not saved: {}", id, refused.getMessage());
			model.addAttribute(ModelAttributes.LETTER_ERROR, message(refused));
			model.addAttribute(ModelAttributes.LETTER_DRAFT, body);
			return showCard(model, userId, id, true);
		}
		latestRequest(userId, id).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.LETTERS_CHANGED);
		return showCard(model, userId, id, false);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.WRITE_COVER_LETTER, id);
		response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.LETTERS_CHANGED);
		return showCard(model, userId, id, false);
	}

	@PostMapping("/cancel")
	String cancel(@PathVariable long id, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		latestRequest(userId, id).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.LETTERS_CHANGED);
		return showCard(model, userId, id, false);
	}

	@PostMapping("/delete")
	String delete(@PathVariable long id, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		this.letters.deleteLetter(userId, id);
		response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.LETTERS_CHANGED);
		return showCard(model, userId, id, false);
	}

	/**
	 * The letter as a page of its own, as its PDF lays it out.
	 */
	@GetMapping
	String preview(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		model.addAllAttributes(this.pdf.variables(requireLetter(userId, id), this.cvs.find(userId).details(),
				requireApplication(userId, id)));
		return Views.COVER_LETTER_DOCUMENT;
	}

	/**
	 * The PDF, named for the person and the company: "Cover letter Ana Pop - Acme.pdf".
	 */
	@GetMapping("/pdf")
	ResponseEntity<byte[]> downloadPdf(@PathVariable long id, Model model, Locale locale) {
		long userId = requireProfile(model).id();
		ApplicationDetails application = requireApplication(userId, id);
		CvDetails details = this.cvs.find(userId).details();
		byte[] content = this.pdf.render(requireLetter(userId, id), details, application, locale);
		return attachment(content, MediaType.APPLICATION_PDF, Attachments.letterName(details.fullName(), application.company()));
	}

	private String showCard(Model model, long userId, long applicationId, boolean editing) {
		Cv cv = this.cvs.find(userId);
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		Optional<CoverLetter> letter = this.letters.findLetter(userId, applicationId);
		model.addAttribute(ModelAttributes.LETTER_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.COVER_LETTER, letter.orElse(null));
		// A letter written without Claude starts from the profile's base letter, filled in for the job
		if (editing && letter.isEmpty() && !model.containsAttribute(ModelAttributes.LETTER_DRAFT)) {
			ApplicationDetails job = requireApplication(userId, applicationId);
			this.letters.findBaseLetter(userId).ifPresent(base -> {
				model.addAttribute(ModelAttributes.LETTER_DRAFT, base.fillIn(job.company(), job.title()));
				model.addAttribute(ModelAttributes.LETTER_FROM_BASE, true);
			});
		}
		model.addAttribute(ModelAttributes.LETTER_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.LETTER_PROMPT,
				request.map(asked -> prompt(model, asked.id())).orElse(null));
		model.addAttribute(ModelAttributes.LETTER_EDITING, editing);
		return Views.COVER_LETTER_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.WRITE_COVER_LETTER, applicationId);
	}

	private ApplicationDetails requireApplication(long userId, long applicationId) {
		return this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

	private CoverLetter requireLetter(long userId, long applicationId) {
		return this.letters.findLetter(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
