package com.jobwarden.web.controller;

import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.exception.OutreachDraftNotFoundException;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.ClaudePrompts;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A job's messages to the people who hire for it: a LinkedIn connection note and an email to each, written by Claude
 * (save_outreach_draft) to the job's contact, its recruiters on the Who's hiring page or whoever posted it. The profile
 * changes them, copies them and sends them itself: JobWarden never sends anything.
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/outreach")
@CustomLog
@RequiredArgsConstructor
class OutreachController extends BaseController {

	private final ApplicationService applications;

	private final CoverLetterService letters;

	private final CvService cvs;

	private final DiscoveryService discovery;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping
	String card(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showCard(model, userId, id);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.WRITE_OUTREACH, id);
		return showCard(model, userId, id);
	}

	@PostMapping("/cancel")
	String cancel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		latestRequest(userId, id).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		return showCard(model, userId, id);
	}

	/**
	 * Keeps one message as changed, and answers with it; when it can't be kept (a note too long for LinkedIn), with
	 * what was typed, saying why.
	 */
	@PostMapping("/{draftId}")
	String save(@PathVariable long id, @PathVariable long draftId,
			@RequestParam(required = false) @Nullable String linkedinNote,
			@RequestParam(required = false) @Nullable String emailSubject,
			@RequestParam(required = false) @Nullable String emailBody, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		OutreachDraft draft = this.letters.findDrafts(userId, id)
			.stream()
			.filter(kept -> kept.id() == draftId)
			.findFirst()
			.orElseThrow(() -> new OutreachDraftNotFoundException(draftId));
		try {
			draft = this.letters.changeDraft(userId, draftId, linkedinNote, emailSubject, emailBody);
		} catch (BusinessRuleException refused) {
			log.debug("Outreach draft {} not saved: {}", draftId, refused.getMessage());
			model.addAttribute(ModelAttributes.OUTREACH_ERROR, message(refused));
			draft = draft.toBuilder().linkedinNote(linkedinNote).emailSubject(emailSubject).emailBody(emailBody).build();
		}
		model.addAttribute(ModelAttributes.OUTREACH_JOB_ID, id);
		model.addAttribute(ModelAttributes.OUTREACH_DRAFT, draft);
		return Views.OUTREACH_DRAFT_FRAGMENT;
	}

	@PostMapping("/{draftId}/delete")
	String delete(@PathVariable long id, @PathVariable long draftId, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		this.letters.deleteDraft(userId, draftId);
		return showCard(model, userId, id);
	}

	private String showCard(Model model, long userId, long applicationId) {
		Cv cv = this.cvs.find(userId);
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.OUTREACH_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.OUTREACH_DRAFTS, this.letters.findDrafts(userId, applicationId));
		model.addAttribute(ModelAttributes.OUTREACH_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.OUTREACH_PROMPT,
				request.map(asked -> ClaudePrompts.forRequest(asked.id(), userId)).orElse(null));
		return Views.OUTREACH_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.WRITE_OUTREACH, applicationId);
	}

	private void requireApplication(long userId, long applicationId) {
		this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
