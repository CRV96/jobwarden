package com.jobwarden.web.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.web.assistant.AssistantRunner;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import com.jobwarden.web.dto.NewProfileForm;
import com.jobwarden.web.user.CurrentUser;
import com.jobwarden.web.view.FormTexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.jobwarden.web.controller.Pages.redirectTo;
import static com.jobwarden.web.controller.Pages.requireProfile;

@PageController
@RequestMapping(ControllerConstants.PROFILES)
@CustomLog
@RequiredArgsConstructor
class ProfileController {

	/** Where the settings page shows the profile's photo */
	private static final String PROFILE_SECTION = ControllerConstants.SETTINGS + "#profile";

	/** Where it offers to delete the profile */
	private static final String DELETE_SECTION = ControllerConstants.SETTINGS + "#delete-profile";

	/** Where it sets, changes and removes the profile's password */
	private static final String PASSWORD_SECTION = ControllerConstants.SETTINGS + "#password";

	/**
	 * The pages ask for a photo with when it changed in its address (profiles/list :: avatar), so the browser keeps it
	 * until it changes.
	 */
	private static final Duration IMAGE_MAX_AGE = Duration.ofDays(365);

	private final UserService users;

	private final CurrentUser currentUser;

	private final DiscoveryService discovery;

	/** Optional: without it, e.g. in a test of one controller, there are no runs to stop. */
	private final ObjectProvider<AssistantRunner> runner;

	private final FormTexts formTexts;

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
			return showProfiles(model, form.name(), this.formTexts.errorMessage(result));
		}
		try {
			this.currentUser.select(this.users.create(form.name()).id());
		} catch (BusinessRuleException exception) {
			log.debug("Profile not created: the name is taken");
			return showProfiles(model, form.name(), this.formTexts.message(exception));
		}
		return redirectTo(ControllerConstants.HOMEPAGE);
	}

	/**
	 * Renames the current profile, and says how it went once, back on the settings page: an invalid or taken name
	 * comes back with the error, as typed.
	 */
	@PostMapping("/rename")
	String rename(@Valid NewProfileForm form, BindingResult result, Model model, RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		if (result.hasErrors()) {
			log.debug("Profile {} not renamed: the name is blank or too long", userId);
			redirect.addFlashAttribute(ModelAttributes.PROFILE_NAME_ERROR, this.formTexts.errorMessage(result));
			redirect.addFlashAttribute(ModelAttributes.PROFILE_NAME_TYPED, form.name());
			return redirectTo(PROFILE_SECTION);
		}
		try {
			this.users.rename(userId, form.name());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_NAME_SAVED, true);
		} catch (BusinessRuleException exception) {
			log.debug("Profile {} not renamed: the name is taken", userId);
			redirect.addFlashAttribute(ModelAttributes.PROFILE_NAME_ERROR, this.formTexts.message(exception));
			redirect.addFlashAttribute(ModelAttributes.PROFILE_NAME_TYPED, form.name());
		}
		return redirectTo(PROFILE_SECTION);
	}

	/**
	 * Deletes the current profile and everything of it, once its name is typed to confirm, and goes to the Profiles
	 * page, which says so. Typed otherwise, nothing is deleted, and the settings page says why.
	 */
	@PostMapping("/delete")
	String delete(@RequestParam(defaultValue = "") String confirm, Model model, RedirectAttributes redirect) {
		User profile = requireProfile(model);
		if (!confirm.strip().equals(profile.name())) {
			log.debug("Profile {} not deleted: its name wasn't typed to confirm", profile.id());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_DELETE_ERROR, true);
			return redirectTo(DELETE_SECTION);
		}
		deleteProfile(profile.id());
		redirect.addFlashAttribute(ModelAttributes.PROFILE_DELETED, profile.name());
		return redirectTo(ControllerConstants.PROFILES);
	}

	/**
	 * Selects the profile, from the Profiles page or the menu. One with a password asks for it first, unless it's the
	 * profile already selected.
	 */
	@PostMapping("/{id}/select")
	String select(@PathVariable long id) {
		boolean selected = this.currentUser.get().map(profile -> profile.id() == id).orElse(false);
		if (!selected && this.users.hasPassword(id)) {
			return redirectTo(ControllerConstants.PROFILES + "/" + id + "/unlock");
		}
		this.currentUser.select(id);
		return redirectTo(ControllerConstants.HOMEPAGE);
	}

	/**
	 * Logs out of the current profile in this browser, from the profile menu: the session goes, and with it the profile
	 * chosen, so the Profiles page comes next, saying so, and a profile with a password asks for it again. The language
	 * stays: it's a cookie of its own.
	 */
	@PostMapping("/logout")
	String logout(Model model, HttpServletRequest request, RedirectAttributes redirect) {
		User profile = requireProfile(model);
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		log.info("Logged out of profile {}", profile.id());
		redirect.addFlashAttribute(ModelAttributes.PROFILE_LOGGED_OUT, profile.name());
		return redirectTo(ControllerConstants.PROFILES);
	}

	/**
	 * Asks for the profile's password. One without a password needs none: back to the Profiles page, to choose it.
	 */
	@GetMapping("/{id}/unlock")
	String unlockForm(@PathVariable long id, Model model) {
		if (!this.users.hasPassword(id)) {
			return redirectTo(ControllerConstants.PROFILES);
		}
		return showUnlock(model, id, null);
	}

	/**
	 * Selects the profile once its password is right, in a session of its own: the browser gets a new session id, so
	 * one someone else planted before doesn't get in with it. Wrong, the page asks again and says why; after a few
	 * wrong ones in a row, the profile waits a minute.
	 */
	@PostMapping("/{id}/unlock")
	String unlock(@PathVariable long id, @RequestParam(defaultValue = "") String password, Model model,
			HttpServletRequest request) {
		try {
			this.users.checkPassword(id, password);
		} catch (BusinessRuleException refused) {
			return showUnlock(model, id, this.formTexts.message(refused));
		}
		// A session made from now on can't have been planted; one there already gets a new id
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		this.currentUser.select(id);
		log.info("Profile {} opened with its password", id);
		return redirectTo(ControllerConstants.HOMEPAGE);
	}

	/**
	 * Gives the current profile a password, or changes the one it has, which then needs the current one. Says how it
	 * went once, back on the settings page.
	 */
	@PostMapping("/password")
	String changePassword(@RequestParam(defaultValue = "") String current, @RequestParam(defaultValue = "") String password,
			@RequestParam(defaultValue = "") String confirm, Model model, RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		try {
			if (!password.equals(confirm)) {
				redirect.addFlashAttribute(ModelAttributes.PROFILE_PASSWORD_ERROR,
						this.formTexts.text("settings.password.mismatch"));
				return redirectTo(PASSWORD_SECTION);
			}
			if (password.isBlank()) {
				throw new BusinessRuleException(IdentityErrorCode.PASSWORD_TOO_SHORT, "No password typed",
						String.valueOf(UserService.PASSWORD_MIN_LENGTH));
			}
			this.users.changePassword(userId, current, password);
			redirect.addFlashAttribute(ModelAttributes.PROFILE_PASSWORD_SAVED, true);
		} catch (BusinessRuleException refused) {
			log.debug("Password of profile {} not changed: {}", userId, refused.getErrorCode());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_PASSWORD_ERROR, this.formTexts.message(refused));
		}
		return redirectTo(PASSWORD_SECTION);
	}

	/**
	 * Removes the current profile's password, given the password: then it opens without one.
	 */
	@PostMapping("/password/remove")
	String removePassword(@RequestParam(defaultValue = "") String current, Model model, RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		try {
			this.users.changePassword(userId, current, null);
			redirect.addFlashAttribute(ModelAttributes.PROFILE_PASSWORD_REMOVED, true);
		} catch (BusinessRuleException refused) {
			log.debug("Password of profile {} not removed: {}", userId, refused.getErrorCode());
			redirect.addFlashAttribute(ModelAttributes.PROFILE_PASSWORD_ERROR, this.formTexts.message(refused));
		}
		return redirectTo(PASSWORD_SECTION);
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
			redirect.addFlashAttribute(ModelAttributes.PROFILE_IMAGE_ERROR, this.formTexts.message(exception));
		}
		return redirectTo(PROFILE_SECTION);
	}

	@PostMapping("/image/remove")
	String removeImage(Model model, RedirectAttributes redirect) {
		this.users.removeImage(requireProfile(model).id());
		redirect.addFlashAttribute(ModelAttributes.PROFILE_IMAGE_REMOVED, true);
		return redirectTo(PROFILE_SECTION);
	}

	private String showUnlock(Model model, long id, @Nullable String error) {
		model.addAttribute(ModelAttributes.UNLOCKING_PROFILE,
				this.users.findById(id).orElseThrow(() -> new UserNotFoundException(id)));
		model.addAttribute(ModelAttributes.UNLOCK_ERROR, error);
		return Views.PROFILE_UNLOCK;
	}

	private String showProfiles(Model model, @Nullable String name, @Nullable String nameError) {
		model.addAttribute(ModelAttributes.PROFILES, this.users.findAll());
		model.addAttribute(ModelAttributes.PROFILES_WITH_PASSWORD, this.users.findIdsWithPassword());
		model.addAttribute(ModelAttributes.NAME, name);
		model.addAttribute(ModelAttributes.NAME_ERROR, nameError);
		return Views.PROFILE_LIST;
	}

	/**
	 * Deletes the profile and everything of it (UserService.delete), stops the assistant's runs on its requests and
	 * deletes what they printed, and selects no profile in this browser session.
	 */
	private void deleteProfile(long userId) {
		List<Long> requestIds = this.discovery.findRequests(userId, Integer.MAX_VALUE).stream().map(AiRequest::id).toList();
		this.runner.ifAvailable(assistantRunner -> assistantRunner.forget(requestIds));
		this.users.delete(userId);
		this.currentUser.forget();
	}

}
