package com.jobwarden.web.controller;

import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.service.StoryService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
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
 * A job's interview brief: what Claude found out about the company and the role, the questions likely asked, the
 * profile's own stories to tell, from its story bank first, and questions to ask them. The job's page asks Claude for it with a request, which
 * Claude Code does through the MCP server (save_interview_brief); its card shows the brief once it's saved.
 */
@PageController
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/prep")
@RequiredArgsConstructor
class InterviewBriefController {

	private final ApplicationService applications;

	private final InterviewBriefService briefs;

	private final DiscoveryService discovery;

	private final StoryService stories;

	private final PromptCards promptCards;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
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
		this.discovery.createRequest(userId, AiRequestKind.PREPARE_INTERVIEW, id);
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
		this.briefs.delete(userId, id);
		return showCard(model, userId, id);
	}

	private String showCard(Model model, long userId, long applicationId) {
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		Optional<InterviewBrief> brief = this.briefs.find(userId, applicationId);
		model.addAttribute(ModelAttributes.PREP_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.INTERVIEW_BRIEF, brief.orElse(null));
		// The story bank's stories it draws on, those still in the bank
		model.addAttribute(ModelAttributes.BRIEF_STORIES,
				brief.map(found -> this.stories.findAll(userId, found.storyIds())).orElse(List.of()));
		model.addAttribute(ModelAttributes.PREP_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.PREP_PROMPT,
				request.map(asked -> this.promptCards.prompt(requireProfile(model), asked.id())).orElse(null));
		return Views.INTERVIEW_BRIEF_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.PREPARE_INTERVIEW, applicationId);
	}

}
