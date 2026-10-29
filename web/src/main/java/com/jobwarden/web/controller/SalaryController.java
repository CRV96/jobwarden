package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.util.List;

import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.StatusSalary;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.SalaryService;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.MarketForm;
import com.jobwarden.web.view.ChartScale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What the profile's jobs pay, compared in one currency and per one period, both picked on the page; and below, what
 * the market pays a position across countries. That section loads after the page (HTMX), since its sources may take
 * a moment to answer.
 */
@Controller
@RequestMapping(ControllerConstants.SALARIES)
@RequiredArgsConstructor
class SalaryController extends BaseController {

	/** The periods to show salaries per: the others are rarely how people think of one. */
	private static final List<SalaryPeriod> PERIODS = List.of(SalaryPeriod.YEAR, SalaryPeriod.MONTH, SalaryPeriod.HOUR);

	private static final String MARKET = "/market";

	private final SalaryService salaries;

	private final MarketSalaryService market;

	/**
	 * @param currency an ISO 4217 code; without one, euros
	 */
	@GetMapping
	String salaries(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, MarketForm marketForm, Model model) {
		long userId = requireProfile(model).id();
		SalaryOverview overview = this.salaries.overview(userId, currency, period);

		model.addAttribute(ModelAttributes.SALARY_OVERVIEW, overview);
		model.addAttribute(ModelAttributes.SALARY_PERIODS, PERIODS);
		model.addAttribute(ModelAttributes.RANGE_SCALE,
				ChartScale.upTo(overview.highest().map(SalaryEntry::high).orElse(BigDecimal.ZERO)));
		model.addAttribute(ModelAttributes.STATUS_SCALE, ChartScale.upTo(
				overview.byStatus().stream().map(StatusSalary::median).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO)));
		model.addAttribute(ModelAttributes.MARKET_FORM, marketForm);
		model.addAttribute(ModelAttributes.MARKET_URL,
				marketForm.url(ControllerConstants.SALARIES + MARKET, overview.currency(), period, null));
		addImages(model, userId);
		return Views.SALARIES;
	}

	/**
	 * Called by HTMX: the market salaries section, for the role, skill and countries picked in it, in the page's
	 * currency and per its period.
	 */
	@GetMapping(MARKET)
	String market(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, MarketForm marketForm, Model model) {
		long userId = requireProfile(model).id();
		addMarket(model, this.salaries.overview(userId, currency, period), marketForm);
		return Views.SALARIES_MARKET_FRAGMENT;
	}

	/**
	 * Called by HTMX from the market section: starts importing the Stack Overflow survey, and answers with the section,
	 * which then asks again until it's done.
	 */
	@PostMapping(MARKET + "/survey")
	String importSurvey(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, MarketForm marketForm, Model model) {
		long userId = requireProfile(model).id();
		this.market.importSurvey();
		addMarket(model, this.salaries.overview(userId, currency, period), marketForm);
		return Views.SALARIES_MARKET_FRAGMENT;
	}

	private void addMarket(Model model, SalaryOverview overview, MarketForm marketForm) {
		MarketSalaries found = this.market.find(marketForm.toQuery(), overview.currency(), overview.period());
		BigDecimal mine = overview.median();
		model.addAttribute(ModelAttributes.MARKET, found);
		model.addAttribute(ModelAttributes.MARKET_FORM, marketForm);
		model.addAttribute(ModelAttributes.MARKET_ROLES, this.market.roles());
		model.addAttribute(ModelAttributes.MARKET_SKILLS, this.market.skills());
		model.addAttribute(ModelAttributes.MARKET_SCALE,
				ChartScale.upTo((mine != null) ? found.highest().max(mine) : found.highest()));
		model.addAttribute(ModelAttributes.SALARY_OVERVIEW, overview);
	}

}
