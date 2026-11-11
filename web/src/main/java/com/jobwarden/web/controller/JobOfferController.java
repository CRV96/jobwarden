package com.jobwarden.web.controller;

import java.util.Map;
import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.OfferForm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * A job's offer, on its page and in its side panel while it's at Offer: the terms, saved as they're typed, which the
 * offers page compares; and the brief Claude writes to negotiate it, with the market salaries JobWarden has. The page
 * asks Claude for the brief with a request, which Claude Code does through the MCP server (save_negotiation_brief).
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}")
@CustomLog
@RequiredArgsConstructor
class JobOfferController extends BaseController {

	private final ApplicationService applications;

	private final OfferService offers;

	private final DiscoveryService discovery;

	/**
	 * The terms card: HTMX loads it after the job.
	 */
	@GetMapping("/offer")
	String terms(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		model.addAttribute(ModelAttributes.OFFER_JOB_ID, id);
		model.addAttribute(ModelAttributes.JOB_OFFER, this.offers.find(userId, id));
		return Views.OFFER_TERMS_FRAGMENT;
	}

	/**
	 * Called by HTMX as the terms change. Returns only what the card's status shows: that they're saved, or why they
	 * aren't. The offers page, or the list behind the side panel, then reloads.
	 */
	@PutMapping("/offer")
	String saveTerms(@PathVariable long id, @Valid OfferForm form, BindingResult result, Model model,
			HttpServletResponse response) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			Map<String, String> errors = fieldErrors(result);
			log.debug("Offer terms of application {} not saved, fields with errors: {}", id, errors.keySet());
			model.addAttribute(ModelAttributes.FIELD_ERRORS, errors);
		} else {
			this.offers.save(userId, id, form.toOffer());
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.APPLICATIONS_CHANGED);
		}
		return Views.OFFER_TERMS_STATUS_FRAGMENT;
	}

	/**
	 * The negotiation card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping("/negotiation")
	String negotiation(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showBrief(model, userId, id);
	}

	@PostMapping("/negotiation/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.NEGOTIATE_OFFER, id);
		return showBrief(model, userId, id);
	}

	@PostMapping("/negotiation/cancel")
	String cancel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		latestRequest(userId, id).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		return showBrief(model, userId, id);
	}

	@PostMapping("/negotiation/delete")
	String delete(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.offers.deleteBrief(userId, id);
		return showBrief(model, userId, id);
	}

	private String showBrief(Model model, long userId, long applicationId) {
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.OFFER_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.NEGOTIATION_BRIEF, this.offers.findBrief(userId, applicationId).orElse(null));
		model.addAttribute(ModelAttributes.NEGOTIATION_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.NEGOTIATION_PROMPT,
				request.map(asked -> prompt(model, asked.id())).orElse(null));
		return Views.NEGOTIATION_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.NEGOTIATE_OFFER, applicationId);
	}

	private void requireApplication(long userId, long applicationId) {
		this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
