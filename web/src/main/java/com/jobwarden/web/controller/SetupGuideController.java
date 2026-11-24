package com.jobwarden.web.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.cv.service.CvService;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.controller.SetupSteps.Step;
import com.jobwarden.web.dto.ContactsForm;
import com.jobwarden.web.view.FormTexts;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static com.jobwarden.web.controller.Pages.requireProfile;

/**
 * The setup guide: what to set up for each part of JobWarden to work, and which of it the profile has done
 * (SetupSteps). app.js opens it on its own while a step is left, and the profile menu while one is (see setup.js). The
 * name and contact details are filled in right in it, since every tailored CV needs them.
 */
@PageController
@CustomLog
@RequiredArgsConstructor
class SetupGuideController {

	private final SetupSteps setupSteps;

	private final CvService cvs;

	private final FormTexts formTexts;

	@GetMapping(ControllerConstants.SETUP)
	String guide(Model model) {
		long userId = requireProfile(model).id();
		model.addAttribute(ModelAttributes.CV_DETAILS, this.cvs.find(userId).details());
		return showGuide(model, userId, Map.of());
	}

	/**
	 * Saves the name and the contact details to the CV page, then shows the guide again, with the step done; or, with
	 * a field wrong, as typed, with why.
	 */
	@PostMapping(ControllerConstants.SETUP + "/contacts")
	String saveContacts(@Valid ContactsForm form, BindingResult result, Model model) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			Map<String, String> errors = this.formTexts.fieldErrors(result);
			log.debug("Contact details of profile {} not saved, fields with errors: {}", userId, errors.keySet());
			model.addAttribute(ModelAttributes.CV_DETAILS, form.toCvDetails());
			return showGuide(model, userId, errors);
		}
		model.addAttribute(ModelAttributes.CV_DETAILS, this.cvs.saveContacts(userId, form.toCvDetails()));
		return showGuide(model, userId, Map.of());
	}

	private String showGuide(Model model, long userId, Map<String, String> fieldErrors) {
		List<Step> steps = this.setupSteps.of(userId);
		model.addAttribute(ModelAttributes.SETUP_STEPS, steps);
		model.addAttribute(ModelAttributes.SETUP_DONE, steps.stream().filter(Step::done).count());
		model.addAttribute(ModelAttributes.FIELD_ERRORS, fieldErrors);
		return Views.SETUP_GUIDE_FRAGMENT;
	}

}
