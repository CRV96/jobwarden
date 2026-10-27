package com.jobwarden.web.controller;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.dto.User;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.HtmxEvents;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.ApplicationListParams;
import com.jobwarden.web.dto.NewApplicationForm;
import com.jobwarden.web.dto.NewEventForm;
import com.jobwarden.web.dto.NotesForm;
import com.jobwarden.web.dto.SalaryForm;
import com.jobwarden.web.enums.ApplicationView;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Every page here needs a profile: {@link #requireProfile} sends the browser to the profile picker if none is
 * selected. An application the profile doesn't own answers 404.
 * <p>
 * An application's parts render on its own page or in the side panel over the list ({@link ApplicationView}); the
 * HTMX requests from them say which, and get that part back.
 */
@Controller
@RequestMapping(ControllerConstants.APPLICATIONS)
@CustomLog
@RequiredArgsConstructor
class ApplicationController extends BaseController {

	private final ApplicationService applications;

	/**
	 * The list, searched, filtered, sorted and paged as its address says. An address past the last page (e.g. after
	 * deleting the last rows of a page) goes to the last page.
	 */
	@GetMapping
	String list(@ModelAttribute(ModelAttributes.LIST_PARAMS) ApplicationListParams listParams, Model model) {
		long userId = requireProfile(model).id();
		Page<ApplicationSummary> page = this.applications.find(userId, listParams.toQuery());
		if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
			return redirectTo(listParams.withPage(page.getTotalPages()).url());
		}
		model.addAttribute(ModelAttributes.APPLICATION_PAGE, page);
		model.addAttribute(ModelAttributes.STATUS_COUNTS, this.applications.countByStatus(userId, listParams.q()));
		addImages(model, userId);
		addFormChoices(model);
		return Views.APPLICATION_LIST;
	}

	@GetMapping("/new")
	String newApplication(Model model) {
		requireProfile(model);
		return showNewApplicationForm(model, null, Map.of());
	}

	/**
	 * Adds an application by hand, from the list's dialog or the full form. Invalid input shows the full form, with
	 * each error next to its field.
	 */
	@PostMapping
	String create(@Valid NewApplicationForm form, BindingResult result, Model model) {
		User user = requireProfile(model);
		if (result.hasErrors()) {
			Map<String, String> errors = fieldErrors(result);
			log.debug("New application not added, fields with errors: {}", errors.keySet());
			return showNewApplicationForm(model, form, errors);
		}
		long id = this.applications.create(form.toNewApplication(user.id()));
		return redirectTo(ControllerConstants.APPLICATIONS + "/" + id);
	}

	@GetMapping("/{id}")
	String detail(@PathVariable long id, Model model) {
		addApplication(model, requireProfile(model).id(), id, ApplicationView.PAGE);
		return Views.APPLICATION_DETAIL;
	}

	/**
	 * The side panel, which HTMX loads when a row of the list or a card of the pipeline is clicked.
	 */
	@GetMapping("/{id}/drawer")
	String drawer(@PathVariable long id, Model model) {
		addApplication(model, requireProfile(model).id(), id, ApplicationView.DRAWER);
		return ApplicationView.DRAWER.getFragment();
	}

	/**
	 * Called by HTMX, both by the add-event form and the quick status buttons. Returns the part the form is in, which
	 * HTMX swaps in place, so the status and the timeline show the new event.
	 */
	@PostMapping("/{id}/events")
	String addEvent(@PathVariable long id, @Valid NewEventForm form, BindingResult result,
			@RequestParam(defaultValue = "PAGE") ApplicationView view, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		String error = result.hasErrors() ? errorMessage(result) : tryAddEvent(userId, id, form);
		if (error != null) {
			log.debug("Event not added to application {}: {}", id, error);
		}
		return showChangedApplication(model, response, userId, id, view, error);
	}

	/**
	 * Called by HTMX from the timeline. Like {@link #addEvent}, returns the part the timeline is in.
	 */
	@DeleteMapping("/{id}/events/{eventId}")
	String removeEvent(@PathVariable long id, @PathVariable long eventId,
			@RequestParam(defaultValue = "PAGE") ApplicationView view, Model model, HttpServletResponse response) {
		long userId = requireProfile(model).id();
		String error = null;
		try {
			this.applications.removeEvent(userId, id, eventId);
		} catch (BusinessRuleException exception) {
			error = message(exception);
			log.debug("Event {} not removed from application {}: {}", eventId, id, error);
		}
		return showChangedApplication(model, response, userId, id, view, error);
	}

	/**
	 * Called by HTMX as the notes are typed. Returns only what the field shows once they're saved.
	 */
	@PutMapping("/{id}/notes")
	String updateNotes(@PathVariable long id, NotesForm form, Model model) {
		this.applications.updateNotes(requireProfile(model).id(), id, form.toNotes());
		return Views.NOTES_SAVED_FRAGMENT;
	}

	/**
	 * Called by HTMX from the salaries page, for a salary it couldn't read. {@code HX-Refresh} then shows the page
	 * with the salary in it.
	 */
	@PostMapping("/{id}/salary")
	ResponseEntity<Void> updateSalary(@PathVariable long id, SalaryForm form, Model model) {
		this.applications.updateSalary(requireProfile(model).id(), id, form.toSalary());
		return refresh();
	}

	/**
	 * Called by HTMX. From the application's page, the {@code HX-Redirect} header sends the browser back to the list;
	 * from the side panel, {@code HX-Refresh} reloads the page behind it, with its search and filters.
	 */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable long id, @RequestParam(defaultValue = "PAGE") ApplicationView view,
			Model model) {
		this.applications.delete(requireProfile(model).id(), id);
		return (view == ApplicationView.DRAWER) ? refresh()
				: ResponseEntity.noContent().header(HtmxHeaders.REDIRECT, ControllerConstants.APPLICATIONS).build();
	}

	/**
	 * Called by HTMX with the applications ticked in the list. The list then reloads without them.
	 */
	@DeleteMapping
	ResponseEntity<Void> deleteAll(@RequestParam List<Long> ids, Model model) {
		this.applications.deleteAll(requireProfile(model).id(), ids);
		return refresh();
	}

	/**
	 * @return why the event wasn't added, or {@code null} if it was
	 */
	private @Nullable String tryAddEvent(long userId, long applicationId, NewEventForm form) {
		try {
			this.applications.addEvent(userId, applicationId, form.toNewTimelineEvent());
			return null;
		} catch (BusinessRuleException exception) {
			return message(exception);
		}
	}

	/**
	 * The part of the application the request came from, after its timeline changed. The list or the pipeline
	 * behind the side panel reloads when HTMX fires the event this sends.
	 *
	 * @param eventError why the change wasn't made, to show next to the timeline, or {@code null}
	 */
	private String showChangedApplication(Model model, HttpServletResponse response, long userId, long applicationId,
			ApplicationView view, @Nullable String eventError) {
		model.addAttribute(ModelAttributes.EVENT_ERROR, eventError);
		addApplication(model, userId, applicationId, view);
		if (eventError == null) {
			response.setHeader(HtmxHeaders.TRIGGER, HtmxEvents.APPLICATIONS_CHANGED);
		}
		return view.getFragment();
	}

	private String showNewApplicationForm(Model model, @Nullable NewApplicationForm form,
			Map<String, String> fieldErrors) {
		model.addAttribute(ModelAttributes.FORM, form);
		model.addAttribute(ModelAttributes.FIELD_ERRORS, fieldErrors);
		addFormChoices(model);
		return Views.NEW_APPLICATION;
	}

	private void addApplication(Model model, long userId, long applicationId, ApplicationView view) {
		model.addAttribute(ModelAttributes.JOB_APPLICATION, this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId)));
		model.addAttribute(ModelAttributes.VIEW, view);
		addImages(model, userId);
		addFormChoices(model);
	}

	/**
	 * What the forms on these pages offer: statuses, rejection reasons, and now as the default date and time.
	 */
	private static void addFormChoices(Model model) {
		model.addAttribute(ModelAttributes.STATUSES, ApplicationStatus.values());
		model.addAttribute(ModelAttributes.REJECTION_REASONS, RejectionReason.values());
		model.addAttribute(ModelAttributes.NOW, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
	}

	private static ResponseEntity<Void> refresh() {
		return ResponseEntity.noContent().header(HtmxHeaders.REFRESH, "true").build();
	}

}
