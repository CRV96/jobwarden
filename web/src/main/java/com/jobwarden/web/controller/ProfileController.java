package com.jobwarden.web.controller;

import java.io.IOException;
import java.time.Duration;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.NewProfileForm;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(ControllerConstants.PROFILES)
@CustomLog
@RequiredArgsConstructor
class ProfileController extends BaseController {

	/** Where the settings page shows the profile's photo */
	private static final String PROFILE_SECTION = ControllerConstants.SETTINGS + "#profile";

	/**
	 * The pages ask for a photo with when it changed in its address (profiles/list :: avatar), so the browser keeps it
	 * until it changes.
	 */
	private static final Duration IMAGE_MAX_AGE = Duration.ofDays(365);

	private final UserService users;

	@GetMapping
	String list(Model model) {
		return showProfiles(model, null, null);
	}

	/**
	 * Creates the profile and selects it. An invalid or taken name shows the page again, with the error.
	 */
	@PostMapping
	String create(@Valid NewProfileForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			log.debug("Profile not created: the name is blank or too long");
			return showProfiles(model, form.name(), errorMessage(result));
		}
		try {
			selectProfile(this.users.create(form.name()).id());
		} catch (BusinessRuleException exception) {
			log.debug("Profile not created: the name is taken");
			return showProfiles(model, form.name(), message(exception));
		}
		return redirectTo(ControllerConstants.APPLICATIONS);
	}

	@PostMapping("/{id}/select")
	String select(@PathVariable long id) {
		selectProfile(id);
		return redirectTo(ControllerConstants.APPLICATIONS);
	}

	/**
	 * A profile's photo, as the menu shows it next to the others' names: any profile's, as there's no sign-in.
	 */
	@GetMapping("/{id}/image")
	ResponseEntity<byte[]> image(@PathVariable long id) {
		return this.users.findImage(id)
			.map(png -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.maxAge(IMAGE_MAX_AGE).cachePrivate())
				.body(png))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * Makes the file the current profile's photo, and says how it went once, back on the settings page.
	 */
	@PostMapping("/image")
	String changeImage(@RequestParam MultipartFile image, Model model, RedirectAttributes redirect) throws IOException {
		long userId = requireProfile(model).id();
		try {
			this.users.changeImage(userId, image.getBytes());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_IMAGE_SAVED, true);
		} catch (BusinessRuleException exception) {
			log.debug("Photo of profile {} not saved: {}", userId, exception.getErrorCode());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_IMAGE_ERROR, message(exception));
		}
		return redirectTo(PROFILE_SECTION);
	}

	@PostMapping("/image/remove")
	String removeImage(Model model, RedirectAttributes redirect) {
		this.users.removeImage(requireProfile(model).id());
		redirect.addFlashAttribute(ModelAttributes.PROFILE_IMAGE_REMOVED, true);
		return redirectTo(PROFILE_SECTION);
	}

	private String showProfiles(Model model, @Nullable String name, @Nullable String nameError) {
		model.addAttribute(ModelAttributes.PROFILES, this.users.findAll());
		model.addAttribute(ModelAttributes.NAME, name);
		model.addAttribute(ModelAttributes.NAME_ERROR, nameError);
		return Views.PROFILE_LIST;
	}

}
