package com.jobwarden.web.controller;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.assistant.PromptCards;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.dto.ContactsForm;
import com.jobwarden.web.dto.CvForm;
import com.jobwarden.web.view.CvPdf;
import com.jobwarden.web.view.FormTexts;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static com.jobwarden.web.controller.Pages.attachment;
import static com.jobwarden.web.controller.Pages.redirectTo;
import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * A job's AI-proof CV: the profile's CV, rewritten by Claude for the job. The job's page asks Claude for it with a
 * request, which Claude Code does through the MCP server (save_tailored_cv); its card shows the CV once it's saved,
 * previewed like the CV page's and downloaded as a PDF, and changed by hand on a page of its own, in the CV page's
 * fields.
 */
@PageController
@RequestMapping(ControllerConstants.APPLICATIONS + "/{id}/cv")
@CustomLog
@RequiredArgsConstructor
class TailoredCvController {

	private final ApplicationService applications;

	private final CvService cvs;

	private final DiscoveryService discovery;

	private final CvPdf pdf;

	private final PromptCards promptCards;

	private final FormTexts formTexts;

	/**
	 * The card: HTMX loads it after the job, and again every few seconds while Claude is on it.
	 */
	@GetMapping("/card")
	String card(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		return showCard(model, userId, id);
	}

	@PostMapping("/request")
	String ask(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		this.discovery.createRequest(userId, AiRequestKind.TAILOR_CV, id);
		return showCard(model, userId, id);
	}

	/**
	 * Saves the name and the contact details to the CV page, which every tailored CV shows, then asks the AI Assistant
	 * to tailor the CV when ask says so; with a field wrong, the card shows them again, as typed, with why.
	 */
	@PostMapping("/contacts")
	String saveContacts(@PathVariable long id, @Valid ContactsForm form, BindingResult result,
			@RequestParam(defaultValue = "false") boolean ask, Model model) {
		long userId = requireProfile(model).id();
		requireApplication(userId, id);
		if (result.hasErrors()) {
			Map<String, String> errors = this.formTexts.fieldErrors(result);
			log.debug("Contact details of profile {} not saved, fields with errors: {}", userId, errors.keySet());
			model.addAttribute(ModelAttributes.FIELD_ERRORS, errors);
			return showCard(model, userId, id, form.toCvDetails());
		}
		this.cvs.saveContacts(userId, form.toCvDetails());
		if (ask) {
			this.discovery.createRequest(userId, AiRequestKind.TAILOR_CV, id);
		}
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
		this.cvs.deleteTailored(userId, id);
		return showCard(model, userId, id);
	}

	/**
	 * The CV for the job, to change by hand: the same fields as the CV page's, with what the AI Assistant said of it, and
	 * its preview.
	 */
	@GetMapping("/edit")
	String edit(@PathVariable long id, Model model) {
		long userId = requireProfile(model).id();
		TailoredCv tailored = requireTailored(userId, id);
		return showEdit(model, requireApplication(userId, id), tailored, tailored.details(), Map.of());
	}

	/**
	 * Saves the fields, keeping what the AI Assistant said of the CV. Invalid ones show the form again, as it was typed,
	 * with the errors.
	 */
	@PostMapping("/edit")
	String saveEdit(@PathVariable long id, @Valid CvForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		ApplicationDetails application = requireApplication(userId, id);
		TailoredCv tailored = requireTailored(userId, id);
		if (result.hasErrors()) {
			Map<String, String> errors = this.formTexts.fieldErrors(result);
			log.debug("CV tailored to application {} of profile {} not saved, fields with errors: {}", id, userId,
					errors.keySet());
			return showEdit(model, application, tailored, form.toCvDetails(), errors);
		}
		// Its form has no name or contact details: the CV page's are the CV's
		this.cvs.saveTailored(userId, id, form.toCvDetails().withContacts(tailored.details()), tailored.matchedKeywords(),
				tailored.missingKeywords(), tailored.notes());
		return redirectTo(ControllerConstants.APPLICATIONS + "/" + id + "/cv/edit?saved");
	}

	private String showEdit(Model model, ApplicationDetails application, TailoredCv tailored, CvDetails shown,
			Map<String, String> fieldErrors) {
		model.addAttribute(ModelAttributes.JOB_APPLICATION, application);
		model.addAttribute(ModelAttributes.TAILORED_CV, tailored);
		model.addAttribute(ModelAttributes.CV_DETAILS, shown);
		model.addAttribute(ModelAttributes.FIELD_ERRORS, fieldErrors);
		return Views.TAILORED_CV_EDIT;
	}

	/**
	 * The CV for the job, as a page of its own, like the CV page's preview.
	 */
	@GetMapping
	String preview(@PathVariable long id, Model model) {
		model.addAttribute(ModelAttributes.CV_DETAILS, requireTailored(requireProfile(model).id(), id).details());
		return Views.CV_DOCUMENT;
	}

	/**
	 * The PDF, named for the person and the company: "CV Ana Pop - Acme.pdf".
	 */
	@GetMapping("/pdf")
	ResponseEntity<byte[]> downloadPdf(@PathVariable long id, Model model, Locale locale) {
		long userId = requireProfile(model).id();
		ApplicationDetails application = requireApplication(userId, id);
		CvDetails details = requireTailored(userId, id).details();
		byte[] document = this.pdf.render(Map.of(ModelAttributes.CV_DETAILS, details), locale);
		return attachment(document, MediaType.APPLICATION_PDF, Attachments.cvName(details.fullName(), application.company()));
	}

	private String showCard(Model model, long userId, long applicationId) {
		return showCard(model, userId, applicationId, null);
	}

	/**
	 * @param contacts the name and contact details its form shows, as typed when they didn't save; the CV page's when
	 * {@code null}
	 */
	private String showCard(Model model, long userId, long applicationId, @Nullable CvDetails contacts) {
		Cv cv = this.cvs.find(userId);
		Optional<AiRequest> request = latestRequest(userId, applicationId);
		model.addAttribute(ModelAttributes.CV_JOB_ID, applicationId);
		model.addAttribute(ModelAttributes.HAS_CV, cv.file() != null || cv.details().isFilledIn());
		model.addAttribute(ModelAttributes.HAS_CONTACTS, cv.details().hasContactDetails());
		model.addAttribute(ModelAttributes.CV_DETAILS, (contacts != null) ? contacts : cv.details());
		model.addAttribute(ModelAttributes.TAILORED_CV, this.cvs.findTailored(userId, applicationId).orElse(null));
		model.addAttribute(ModelAttributes.CV_REQUEST, request.orElse(null));
		model.addAttribute(ModelAttributes.CV_PROMPT,
				request.map(asked -> this.promptCards.prompt(requireProfile(model), asked.id())).orElse(null));
		return Views.TAILORED_CV_FRAGMENT;
	}

	private Optional<AiRequest> latestRequest(long userId, long applicationId) {
		return this.discovery.findLatestRequest(userId, AiRequestKind.TAILOR_CV, applicationId);
	}

	private ApplicationDetails requireApplication(long userId, long applicationId) {
		return this.applications.findById(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

	private TailoredCv requireTailored(long userId, long applicationId) {
		return this.cvs.findTailored(userId, applicationId)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

}
