package com.jobwarden.web.controller;

import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.StoryService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.StoryForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The CV page's story bank: what the profile did, told the STAR way (situation, task, action, result), kept with its
 * CV for interviews; the interview briefs draw on them. Written here, or drafted by Claude from the CV (a WRITE_STORIES
 * request, save_stories) for the profile to check and finish. The card loads after the page and swaps after each
 * change (HTMX).
 */
@Controller
@RequestMapping(ControllerConstants.CV + "/stories")
@RequiredArgsConstructor
class StoryController extends BaseController {

	private final StoryService stories;

	private final CvService cvs;

	private final DiscoveryService discovery;

	/**
	 * The card: HTMX loads it after the page, and again every few seconds while Claude drafts stories.
	 */
	@GetMapping
	String card(Model model) {
		return showCard(model, requireProfile(model).id(), null);
	}

	/**
	 * The card with an empty story to write.
	 */
	@GetMapping("/new")
	String newStory(Model model) {
		return showCard(model, requireProfile(model).id(), StoryForm.NEW);
	}

	/**
	 * The card with one of the stories to change.
	 */
	@GetMapping("/{storyId}/edit")
	String edit(@PathVariable long storyId, Model model) {
		long userId = requireProfile(model).id();
		return showCard(model, userId, StoryForm.of(this.stories.find(userId, storyId)));
	}

	/**
	 * Keeps the story written or changed; when it can't be kept, the form stays open with what was typed, saying why.
	 */
	@PostMapping
	String save(@Valid StoryForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			model.addAttribute(ModelAttributes.STORY_ERROR, errorMessage(result));
			return showCard(model, userId, form);
		}
		try {
			this.stories.save(userId, form.toStory());
		} catch (BusinessRuleException refused) {
			model.addAttribute(ModelAttributes.STORY_ERROR, message(refused));
			return showCard(model, userId, form);
		}
		return showCard(model, userId, null);
	}

	@PostMapping("/{storyId}/delete")
	String delete(@PathVariable long storyId, Model model) {
		long userId = requireProfile(model).id();
		this.stories.delete(userId, storyId);
		return showCard(model, userId, null);
	}

	@PostMapping("/request")
	String ask(Model model) {
		long userId = requireProfile(model).id();
		if (latestRequest(userId).filter(request -> request.status().isOpen()).isEmpty()) {
			this.discovery.createRequest(userId, AiRequestKind.WRITE_STORIES, AiRequirements.NONE);
		}
		return showCard(model, userId, null);
	}

	@PostMapping("/cancel")
	String cancel(Model model) {
		long userId = requireProfile(model).id();
		latestRequest(userId).filter(request -> request.status().isOpen())
			.ifPresent(request -> this.discovery.cancelRequest(userId, request.id()));
		return showCard(model, userId, null);
	}

	/**
	 * @param form the story being written or changed, or {@code null} for none
	 */
	private String showCard(Model model, long userId, @Nullable StoryForm form) {
		Cv cv = this.cvs.find(userId);
		Optional<AiRequest> request = latestRequest(userId);
		model.addAttribute(ModelAttributes.STORIES, this.stories.findAll(userId));
		model.addAttribute(ModelAttributes.STORY_FORM, form);
		model.addAttribute(ModelAttributes.MAX_STORIES, Story.MAX_STORIES);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.STORY_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.STORY_PROMPT,
				request.map(asked -> prompt(model, asked.id())).orElse(null));
		return Views.STORIES_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.WRITE_STORIES);
	}

}
