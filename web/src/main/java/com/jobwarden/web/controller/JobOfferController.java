package com.jobwarden.web.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.OfferExtra;
import com.jobwarden.jobs.dto.SalaryRange;
import com.jobwarden.jobs.enums.OfferExtraKind;
import com.jobwarden.jobs.enums.PayFrequency;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.jobs.service.SalaryService;
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
import org.jspecify.annotations.Nullable;

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

	/** What an offer's base salary can pay for: a salary, or a contractor's rate. */
	private static final List<SalaryPeriod> OFFER_PERIODS = List.of(SalaryPeriod.YEAR, SalaryPeriod.MONTH, SalaryPeriod.DAY,
			SalaryPeriod.HOUR);

	private final ApplicationService applications;

	private final OfferService offers;

	private final DiscoveryService discovery;

	private final SalaryService salaries;

	/**
	 * The terms card: HTMX loads it after the job.
	 */
	@GetMapping("/offer")
	String terms(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		JobOffer offer = this.offers.find(userId, id);
		model.addAttribute(ModelAttributes.OFFER_JOB_ID, id);
		model.addAttribute(ModelAttributes.JOB_OFFER, offer);
		// The figure to start from: the one picked, else the words saved before, else the posting's salary, read
		Optional<SalaryRange> start = offer.baseSalaryRange()
			.or(() -> this.salaries.read(offer.baseSalary()))
			.or(() -> (offer.baseSalary() == null) ? this.applications.findById(userId, id)
				.flatMap(job -> this.salaries.read(job.salary())) : Optional.empty());
		model.addAttribute(ModelAttributes.SALARY_RANGE, start.orElse(null));
		// All in, from the figure the card shows, saved or not yet
		JobOffer shown = start.filter(range -> offer.baseSalaryRange().isEmpty())
			.map(range -> offer.toBuilder()
				.baseSalaryAmount(range.low())
				.baseSalaryCurrency(range.currency())
				.baseSalaryPeriod(range.period())
				.baseSalary(null)
				.build())
			.orElse(offer);
		model.addAttribute(ModelAttributes.SALARY_CURRENCIES, this.salaries.currencies());
		model.addAttribute(ModelAttributes.PAY_PERIODS, OFFER_PERIODS);
		model.addAttribute(ModelAttributes.OFFER_EXTRA_KINDS, OfferExtraKind.values());
		model.addAttribute(ModelAttributes.PAY_FREQUENCIES, PayFrequency.values());
		model.addAttribute(ModelAttributes.OFFER_TOTAL, this.offers.total(shown).orElse(null));
		return Views.OFFER_TERMS_FRAGMENT;
	}

	/**
	 * Called by HTMX as the terms change. Returns what the card's status shows, that they're saved or why they aren't,
	 * and once saved, what the offer pays all in, in its place out of band. The offers page, or the list behind the side
	 * panel, then reloads.
	 */
	@PutMapping("/offer")
	String saveTerms(@PathVariable long id, @Valid OfferForm form, BindingResult result, Model model,
			HttpServletResponse response) {
		long userId = requireProfile(model).id();
		Map<String, String> errors = fieldErrors(result);
		errors.putAll(currencyErrors(form));
		if (!errors.isEmpty()) {
			log.debug("Offer terms of application {} not saved, fields with errors: {}", id, errors.keySet());
			model.addAttribute(ModelAttributes.FIELD_ERRORS, errors);
		} else {
			JobOffer saved = this.offers.save(userId, id, form.toOffer());
			model.addAttribute(ModelAttributes.OFFER_JOB_ID, id);
			model.addAttribute(ModelAttributes.OFFER_TOTAL, this.offers.total(saved).orElse(null));
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.APPLICATIONS_CHANGED);
		}
		return Views.OFFER_TERMS_STATUS_FRAGMENT;
	}

	/**
	 * The currencies the form sends, which must be ones there's an exchange rate for, as its lists offer: the base
	 * salary's, and each extra's (or % of the base salary). Anything else would save, then not compare.
	 */
	private Map<String, String> currencyErrors(OfferForm form) {
		Set<String> currencies = Set.copyOf(this.salaries.currencies());
		Map<String, String> errors = new LinkedHashMap<>();
		String base = blankToNull(form.baseSalaryCurrency());
		if (form.baseSalaryAmount() != null && base != null && !currencies.contains(base.toUpperCase(Locale.ROOT))) {
			errors.put("baseSalaryCurrency", text("validation.offer.currency"));
		}
		List<OfferForm.ExtraRow> extras = (form.extras() == null) ? List.of() : form.extras();
		for (int i = 0; i < extras.size(); i++) {
			OfferForm.ExtraRow extra = extras.get(i);
			String unit = (extra == null) ? null : blankToNull(extra.unit());
			if (extra != null && extra.amount() != null && unit != null && !OfferExtra.SHARE_OF_BASE.equals(unit)
					&& !currencies.contains(unit.toUpperCase(Locale.ROOT))) {
				errors.put("extras[" + i + "].unit", text("validation.offer.currency"));
			}
		}
		return errors;
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	/**
	 * The negotiation card: HTMX loads it after the job, and again every few seconds while the assistant is on it.
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
