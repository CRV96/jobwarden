package com.jobwarden.web.controller;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Every application as a card, in a column per status.
 */
@Controller
@RequestMapping(ControllerConstants.PIPELINE)
@RequiredArgsConstructor
class PipelineController extends BaseController {

	private final ApplicationService applications;

	@GetMapping
	String board(Model model) {
		long userId = requireProfile(model).id();
		Map<ApplicationStatus, List<ApplicationSummary>> columns = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus status : ApplicationStatus.values()) {
			columns.put(status, new ArrayList<>());
		}
		// Newest first in each column, the order findAll returns them in
		for (ApplicationSummary application : this.applications.findAll(userId, null)) {
			columns.get(application.status()).add(application);
		}
		model.addAttribute(ModelAttributes.PIPELINE_COLUMNS, columns);
		// For the add-job dialog
		model.addAttribute(ModelAttributes.STATUSES, ApplicationStatus.values());
		return Views.PIPELINE;
	}

}
