package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.util.List;

import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.SalaryOverview;
import com.jobwarden.jobs.dto.StatusSalary;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.SalaryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.ChartScale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What the profile's jobs pay, compared in one currency and per one period, both picked on the page.
 */
@Controller
@RequestMapping(ControllerConstants.SALARIES)
@RequiredArgsConstructor
class SalaryController extends BaseController {

	/** The periods to show salaries per: the others are rarely how people think of one. */
	private static final List<SalaryPeriod> PERIODS = List.of(SalaryPeriod.YEAR, SalaryPeriod.MONTH, SalaryPeriod.HOUR);

	private final SalaryService salaries;

	/**
	 * @param currency an ISO 4217 code; without one, the one most of the salaries are in
	 */
	@GetMapping
	String salaries(@RequestParam(required = false) @Nullable String currency,
			@RequestParam(defaultValue = "YEAR") SalaryPeriod period, Model model) {
		long userId = requireProfile(model).id();
		SalaryOverview overview = this.salaries.overview(userId, currency, period);

		model.addAttribute(ModelAttributes.SALARY_OVERVIEW, overview);
		model.addAttribute(ModelAttributes.SALARY_PERIODS, PERIODS);
		model.addAttribute(ModelAttributes.RANGE_SCALE,
				ChartScale.upTo(overview.highest().map(SalaryEntry::high).orElse(BigDecimal.ZERO)));
		model.addAttribute(ModelAttributes.STATUS_SCALE, ChartScale.upTo(
				overview.byStatus().stream().map(StatusSalary::median).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO)));
		addImages(model, userId);
		return Views.SALARIES;
	}

}
