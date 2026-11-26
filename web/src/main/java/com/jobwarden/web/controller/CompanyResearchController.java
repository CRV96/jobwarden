package com.jobwarden.web.controller;

import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CompanyResearchService;
import com.jobwarden.web.assistant.PromptCards;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import lombok.RequiredArgsConstructor;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * What the AI Assistant found out on the web about a job's company: its ratings, what its people praise and complain
 * about, what it pays, its benefits, its news and how its interviews go. The job's page asks for it with a request,
 * which the assistant does through the MCP server (save_company_research); its card shows the research once it's saved.
 */
@PageController
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/research")
@RequiredArgsConstructor
class CompanyResearchController {

	private final ApplicationService applications;

	private final CompanyResearchService research;

	private final DiscoveryService discovery;

	private final PromptCards promptCards;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while the assistant is on it.
	 */
	@GetMapping
	String card(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		if (this.applications.findById(userId, id).isEmpty()) {
			throw new ApplicationNotFoundException(id);
		}
		return showCard(model, userId, id);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.RESEARCH_COMPANY, id);
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
		this.research.delete(userId, id);
		return showCard(model, userId, id);
	}

	private String showCard(Model model, long userId, long applicationId) {
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.RESEARCH_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.COMPANY_RESEARCH, this.research.find(userId, applicationId).orElse(null));
		model.addAttribute(ModelAttributes.RESEARCH_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.RESEARCH_PROMPT,
				request.map(asked -> this.promptCards.prompt(requireProfile(model), asked.id())).orElse(null));
		return Views.COMPANY_RESEARCH_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.RESEARCH_COMPANY, applicationId);
	}

}
