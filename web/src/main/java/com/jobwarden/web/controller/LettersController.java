package com.jobwarden.web.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.CoverLetter;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.view.LetterFilter;
import com.jobwarden.web.view.LetterRow;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Every job's cover letter and messages in one place. The profile's base letter on top, that each job's letter starts
 * from; the jobs in use, each with its letter or none, Claude writing it, and its messages; and beside them, the job
 * picked, with its letter and messages to write, change, ask Claude for, preview and download, as on the job's own
 * page (CoverLetterController, OutreachController). Jobs that ended show only when they have a letter. Ticked jobs
 * without a letter go to Claude at once: a request each, done one after the other from one prompt.
 */
@Controller
@RequestMapping(ControllerConstants.LETTERS)
@CustomLog
@RequiredArgsConstructor
class LettersController extends BaseController {

	/** The most jobs the page lists, those furthest from applying last. */
	static final int MAX_JOBS = 300;

	/** The most letters asked of Claude at once: one Claude Code session writes them all, reading each posting. */
	static final int MAX_ASKED = 10;

	private final ApplicationService applications;

	private final CoverLetterService letters;

	private final CvService cvs;

	private final DiscoveryService discovery;

	/**
	 * @param job the job whose letter shows beside the list, or none
	 */
	@GetMapping
	String page(@RequestParam(defaultValue = "ALL") LetterFilter show, @RequestParam(required = false) @Nullable Long job,
			Model model) {
		long userId = requireProfile(model).id();
		List<LetterRow> rows = rows(userId);
		Map<LetterFilter, Long> counts = new EnumMap<>(LetterFilter.class);
		for (LetterFilter filter : LetterFilter.values()) {
			counts.put(filter, rows.stream().filter(filter::matches).count());
		}
		model.addAttribute(ModelAttributes.LETTER_ROWS, rows.stream().filter(show::matches).toList());
		model.addAttribute(ModelAttributes.LETTER_FILTER, show);
		model.addAttribute(ModelAttributes.LETTER_FILTERS, Arrays.asList(LetterFilter.values()));
		model.addAttribute(ModelAttributes.LETTER_COUNTS, counts);
		model.addAttribute(ModelAttributes.LETTER_TOTAL, rows.size());
		model.addAttribute(ModelAttributes.HAS_CV, hasCv(userId));
		model.addAttribute(ModelAttributes.LETTERS_MAX_ASKED, MAX_ASKED);
		addBaseLetter(model, userId, false);
		addWaiting(model, userId);
		Optional<ApplicationDetails> picked = (job != null) ? this.applications.findById(userId, job) : Optional.empty();
		model.addAttribute(ModelAttributes.LETTERS_SELECTED, picked.map(ApplicationDetails::id).orElse(null));
		model.addAttribute(ModelAttributes.LETTERS_JOB, picked.orElse(null));
		addImages(model, userId);
		return Views.LETTERS;
	}

	/**
	 * Called by HTMX as a job is picked: its letter and messages, beside the list.
	 */
	@GetMapping("/jobs/{id}")
	String panel(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		model.addAttribute(ModelAttributes.LETTERS_JOB, requireApplication(userId, id));
		addImages(model, userId);
		return Views.LETTERS_PANEL_FRAGMENT;
	}

	/**
	 * Called by HTMX: one job of the list, while Claude writes its letter, and as its letter changes beside it.
	 *
	 * @param selected the job picked, to mark it
	 */
	@GetMapping("/rows/{id}")
	String row(@PathVariable long id, @RequestParam(required = false) @Nullable Long selected, Model model) {
		long userId = requireProfile(model).id();
		ApplicationDetails job = requireApplication(userId, id);
		Map<Long, AiRequest> writing = writing(userId);
		model.addAttribute(ModelAttributes.LETTER_ROW, LetterRow.of(job, this.letters.findLetter(userId, id).orElse(null),
				writing.get(id), this.letters.countDrafts(userId, List.of(id)).getOrDefault(id, 0)));
		model.addAttribute(ModelAttributes.LETTERS_SELECTED, selected);
		model.addAttribute(ModelAttributes.HAS_CV, hasCv(userId));
		addImages(model, userId);
		return Views.LETTERS_ROW_FRAGMENT;
	}

	/**
	 * Called by HTMX while Claude has letters to write: how many, and what to paste into Claude Code for those waiting.
	 */
	@GetMapping("/waiting")
	String waiting(Model model) {
		addWaiting(model, requireProfile(model).id());
		return Views.LETTERS_WAITING_FRAGMENT;
	}

	/**
	 * Asks Claude for the letters of the jobs ticked, at most {@link #MAX_ASKED}: a request for each job without a letter
	 * that Claude isn't writing already. Nothing without a CV, which Claude writes them from.
	 *
	 * @param ids the jobs ticked
	 * @param selected the job picked, to keep it marked
	 */
	@PostMapping("/request")
	String ask(@RequestParam(name = "ids", required = false) @Nullable List<Long> ids,
			@RequestParam(required = false) @Nullable Long selected, Model model) {
		long userId = requireProfile(model).id();
		List<Long> asked = new ArrayList<>();
		if (ids != null && hasCv(userId)) {
			Set<Long> writing = writing(userId).keySet();
			Set<Long> written = this.letters.findJobsWithLetter(userId, ids);
			for (long id : new LinkedHashSet<>(ids)) {
				if (asked.size() == MAX_ASKED) {
					break;
				}
				if (!writing.contains(id) && !written.contains(id) && this.applications.findById(userId, id).isPresent()) {
					this.discovery.createRequest(userId, AiRequestKind.WRITE_COVER_LETTER, id);
					asked.add(id);
				}
			}
			log.info("Profile {} asked Claude for {} cover letters at once", userId, asked.size());
		}
		return showAsked(model, userId, asked, selected);
	}

	/**
	 * Takes back every letter Claude has still to write, or is writing.
	 */
	@PostMapping("/cancel")
	String cancel(@RequestParam(required = false) @Nullable Long selected, Model model) {
		long userId = requireProfile(model).id();
		List<Long> cancelled = new ArrayList<>();
		for (AiRequest request : openLetterRequests(userId)) {
			this.discovery.cancelRequest(userId, request.id());
			cancelled.add(request.applicationId());
		}
		return showAsked(model, userId, cancelled, selected);
	}

	/**
	 * The base letter's card, as it is.
	 */
	@GetMapping("/base")
	String baseLetter(Model model) {
		addBaseLetter(model, requireProfile(model).id(), false);
		return Views.BASE_LETTER_FRAGMENT;
	}

	/**
	 * The base letter in a field to change, or to write.
	 */
	@GetMapping("/base/edit")
	String editBaseLetter(Model model) {
		addBaseLetter(model, requireProfile(model).id(), true);
		return Views.BASE_LETTER_FRAGMENT;
	}

	/**
	 * Keeps the base letter; when it can't be kept (empty, too long), the field stays open with what was typed, saying
	 * why.
	 */
	@PostMapping("/base")
	String saveBaseLetter(@RequestParam(defaultValue = "") String body, Model model) {
		long userId = requireProfile(model).id();
		try {
			this.letters.saveBaseLetter(userId, body);
		} catch (BusinessRuleException refused) {
			log.debug("Base letter of profile {} not saved: {}", userId, refused.getMessage());
			model.addAttribute(ModelAttributes.BASE_LETTER_ERROR, message(refused));
			model.addAttribute(ModelAttributes.BASE_LETTER_DRAFT, body);
			addBaseLetter(model, userId, true);
			return Views.BASE_LETTER_FRAGMENT;
		}
		addBaseLetter(model, userId, false);
		return Views.BASE_LETTER_FRAGMENT;
	}

	@PostMapping("/base/delete")
	String deleteBaseLetter(Model model) {
		long userId = requireProfile(model).id();
		this.letters.deleteBaseLetter(userId);
		addBaseLetter(model, userId, false);
		return Views.BASE_LETTER_FRAGMENT;
	}

	/**
	 * The jobs in use, saved first, those that ended only with a letter; each with its letter, Claude writing it, and
	 * how many people its messages are to.
	 */
	private List<LetterRow> rows(long userId) {
		List<ApplicationSummary> jobs = this.applications.find(userId, ApplicationQuery.builder()
			.archived(false)
			.sort(ApplicationSort.STATUS)
			.direction(Sort.Direction.ASC)
			.page(0)
			.size(MAX_JOBS)
			.build()).getContent();
		List<Long> ids = jobs.stream().map(ApplicationSummary::id).toList();
		Map<Long, CoverLetter> written = this.letters.findLetters(userId, ids);
		Map<Long, Integer> messages = this.letters.countDrafts(userId, ids);
		Map<Long, AiRequest> writing = writing(userId);
		return jobs.stream()
			.filter(job -> !job.status().hasEnded() || written.containsKey(job.id()))
			.map(job -> LetterRow.of(job, written.get(job.id()), writing.get(job.id()), messages.getOrDefault(job.id(), 0)))
			.toList();
	}

	/** The letters asked for or cancelled: the letters waiting, and those jobs' rows, swapped in place (out of band). */
	private String showAsked(Model model, long userId, List<Long> jobIds, @Nullable Long selected) {
		Map<Long, CoverLetter> written = this.letters.findLetters(userId, jobIds);
		Map<Long, Integer> messages = this.letters.countDrafts(userId, jobIds);
		Map<Long, AiRequest> writing = writing(userId);
		List<LetterRow> rows = new ArrayList<>();
		for (long id : jobIds) {
			this.applications.findById(userId, id)
				.ifPresent(job -> rows.add(LetterRow.of(job, written.get(id), writing.get(id), messages.getOrDefault(id, 0))));
		}
		model.addAttribute(ModelAttributes.LETTERS_ASKED, rows);
		model.addAttribute(ModelAttributes.LETTERS_SELECTED, selected);
		model.addAttribute(ModelAttributes.HAS_CV, hasCv(userId));
		addWaiting(model, userId);
		addImages(model, userId);
		return Views.LETTERS_ASKED_FRAGMENT;
	}

	/** How many letters Claude has to write or is writing, and what to paste into Claude Code for those waiting. */
	private void addWaiting(Model model, long userId) {
		List<AiRequest> open = openLetterRequests(userId);
		List<Long> waiting = open.stream()
			.filter(request -> request.status() == AiRequestStatus.WAITING)
			.map(AiRequest::id)
			.toList();
		model.addAttribute(ModelAttributes.LETTERS_OPEN, open.size());
		model.addAttribute(ModelAttributes.LETTERS_WAITING, waiting.size());
		model.addAttribute(ModelAttributes.LETTERS_PROMPT,
				waiting.isEmpty() ? null : prompt(model, waiting));
	}

	private void addBaseLetter(Model model, long userId, boolean editing) {
		model.addAttribute(ModelAttributes.BASE_LETTER, this.letters.findBaseLetter(userId).orElse(null));
		model.addAttribute(ModelAttributes.BASE_LETTER_EDITING, editing);
	}

	/** The jobs Claude has a letter to write for, or is writing one, by the job's id. */
	private Map<Long, AiRequest> writing(long userId) {
		return openLetterRequests(userId).stream()
			.collect(Collectors.toMap(AiRequest::applicationId, Function.identity(), (older, newer) -> newer));
	}

	/** The requests for a letter Claude has still to do, the oldest first. */
	private List<AiRequest> openLetterRequests(long userId) {
		return this.discovery.findOpenRequests(userId)
			.stream()
			.filter(request -> request.kind() == AiRequestKind.WRITE_COVER_LETTER && request.applicationId() != null)
			.toList();
	}

	private boolean hasCv(long userId) {
		Cv cv = this.cvs.find(userId);
		return cv.file() != null || cv.details().isFilledIn();
	}

	private ApplicationDetails requireApplication(long userId, long applicationId) {
		return this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
