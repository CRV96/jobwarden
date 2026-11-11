package com.jobwarden.web.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.MarketForm;
import com.jobwarden.web.view.OfferMarket;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The profile's offers side by side: each job at Offer with the terms typed in on its page, its salary in one currency
 * and per one period, both picked on the page, and what the market pays the role in its country. That row loads after
 * the page (HTMX), as the market's sources may take a moment to answer. A click on an offer opens its job in the side
 * panel, where its terms are changed and Claude writes the brief to negotiate it.
 */
@Controller
@RequestMapping(ControllerConstants.OFFERS)
@RequiredArgsConstructor
class OfferController extends BaseController {

	/** The periods to show salaries per, like the salaries page. */
	private static final List<SalaryPeriod> PERIODS = List.of(SalaryPeriod.YEAR, SalaryPeriod.MONTH, SalaryPeriod.HOUR);

	private static final String MARKET = "/market";

	private final OfferService offers;

	private final MarketSalaryService market;

	/**
	 * @param currency an ISO 4217 code; without one, euros
	 * @param role the role to compare with, as the Stack Overflow survey names it; the salaries page's default without
	 * one
	 */
	@GetMapping
	String offers(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, @RequestParam(required = false) @Nullable String role,
			@RequestParam(required = false) @Nullable String skill, Model model) {
		long userId = requireProfile(model).id();
		OfferComparison comparison = this.offers.compare(userId, currency, period);
		MarketForm marketForm = new MarketForm(role, skill, null);
		model.addAttribute(ModelAttributes.OFFER_COMPARISON, comparison);
		model.addAttribute(ModelAttributes.SALARY_PERIODS, PERIODS);
		model.addAttribute(ModelAttributes.MARKET_FORM, marketForm);
		model.addAttribute(ModelAttributes.MARKET_ROLES, this.market.roles());
		model.addAttribute(ModelAttributes.MARKET_SKILLS, this.market.skills());
		model.addAttribute(ModelAttributes.OFFER_MARKET_URL, UriComponentsBuilder.fromPath(ControllerConstants.OFFERS + MARKET)
			.queryParam("currency", comparison.currency())
			.queryParam("period", period)
			.queryParam("role", marketForm.selectedRole())
			.queryParam("skill", marketForm.selectedSkill())
			.encode()
			.build()
			.toUriString());
		model.addAttribute(ModelAttributes.TODAY, LocalDate.now());
		addImages(model, userId);
		return Views.OFFERS;
	}

	/**
	 * Called by HTMX: the row of what the market pays beside each offer, for the role and skill picked, in the page's
	 * currency and per its period.
	 */
	@GetMapping(MARKET)
	String market(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, @RequestParam(required = false) @Nullable String role,
			@RequestParam(required = false) @Nullable String skill, Model model) {
		long userId = requireProfile(model).id();
		OfferComparison comparison = this.offers.compare(userId, currency, period);
		model.addAttribute(ModelAttributes.OFFER_COMPARISON, comparison);
		model.addAttribute(ModelAttributes.OFFER_MARKETS, markets(comparison, new MarketForm(role, skill, null)));
		return Views.OFFERS_MARKET_FRAGMENT;
	}

	/**
	 * The market beside each offer whose location names one country, all asked at once.
	 */
	private Map<Long, OfferMarket> markets(OfferComparison comparison, MarketForm form) {
		List<String> countries = comparison.offers()
			.stream()
			.map(offer -> OfferMarket.countryOf(offer.application().location()).orElse(null))
			.filter(Objects::nonNull)
			.distinct()
			.limit(MarketForm.MAX_COUNTRIES)
			.toList();
		Map<Long, OfferMarket> markets = new LinkedHashMap<>();
		if (countries.isEmpty()) {
			return markets;
		}
		MarketQuery query = form.toQuery();
		MarketSalaries found = this.market.find(new MarketQuery(query.role(), query.skill(), countries),
				comparison.currency(), comparison.period());
		for (ComparedOffer offer : comparison.offers()) {
			Optional<OfferMarket> market = OfferMarket.of(offer, found);
			market.ifPresent(beside -> markets.put(offer.application().id(), beside));
		}
		return markets;
	}

}
