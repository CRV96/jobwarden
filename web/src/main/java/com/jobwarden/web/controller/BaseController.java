package com.jobwarden.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.jobwarden.common.exception.JobWardenException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.user.CurrentUser;
import com.jobwarden.web.user.ProfileNotSelectedException;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;

abstract class BaseController {

	private CurrentUser currentUser;

	private UserService users;

	private ApplicationService applications;

	private DiscoveryService discovery;

	private MessageSource messages;

	private @Nullable UpdateService updates;

	/**
	 * Setters rather than a constructor, so subclasses keep their Lombok constructors: those can't pass arguments up
	 * to this class.
	 */
	@Autowired
	final void setCurrentUser(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	@Autowired
	final void setUsers(UserService users) {
		this.users = users;
	}

	@Autowired
	final void setApplications(ApplicationService applications) {
		this.applications = applications;
	}

	@Autowired
	final void setDiscovery(DiscoveryService discovery) {
		this.discovery = discovery;
	}

	@Autowired
	final void setMessages(MessageSource messages) {
		this.messages = messages;
	}

	/**
	 * Optional: without it, e.g. in a test of one controller, the pages say nothing about updates.
	 */
	@Autowired(required = false)
	final void setUpdates(UpdateService updates) {
		this.updates = updates;
	}

	/**
	 * Spring calls this before every handler method of a subclass, so every page has the selected profile (or
	 * {@code null}) for the sidebar in layout.html. It's the only place a request looks the profile up.
	 * <p>
	 * The sidebar also lists the profiles to switch to, counts the profile's applications and the found jobs waiting
	 * on the AI Assistant page, and says when a newer JobWarden is out. HTMX requests skip those: they only swap a part
	 * of the page, without the sidebar.
	 */
	@ModelAttribute
	void layout(Model model, @RequestHeader(name = HtmxHeaders.REQUEST, required = false) @Nullable String htmx) {
		User profile = this.currentUser.get().orElse(null);
		model.addAttribute(ModelAttributes.CURRENT_PROFILE, profile);
		if (htmx != null) {
			return;
		}
		model.addAttribute(ModelAttributes.SIDEBAR_PROFILES, this.users.findAll());
		if (this.updates != null) {
			model.addAttribute(ModelAttributes.UPDATE, this.updates.status());
		}
		if (profile != null) {
			model.addAttribute(ModelAttributes.APPLICATION_COUNT, this.applications.count(profile.id()));
			model.addAttribute(ModelAttributes.DISCOVER_COUNT, this.discovery.countToReview(profile.id(), null));
		}
	}

	/**
	 * For pages that need a profile: returns the one {@link #layout} put in the model.
	 * @throws ProfileNotSelectedException if none is selected, which sends the browser to the profile picker
	 */
	protected static User requireProfile(Model model) {
		if (model.getAttribute(ModelAttributes.CURRENT_PROFILE) instanceof User profile) {
			return profile;
		}
		throw new ProfileNotSelectedException();
	}

	/**
	 * Which images the pages showing applications have: the sites' icons, next to each site, and the companies'
	 * logos, before each job's title.
	 */
	protected void addImages(Model model, long userId) {
		model.addAttribute(ModelAttributes.ICON_SOURCES, this.applications.findSourcesWithIcon(userId));
		model.addAttribute(ModelAttributes.LOGO_APPLICATIONS, this.applications.findApplicationsWithLogo(userId));
	}

	protected void selectProfile(long userId) {
		this.currentUser.select(userId);
	}

	protected static String redirectTo(String path) {
		return "redirect:" + path;
	}

	/**
	 * A file to download, under its name, which may have any letters: browsers that only read ASCII names get one
	 * with the others left out.
	 */
	protected static ResponseEntity<byte[]> attachment(byte[] content, MediaType type, String fileName) {
		return Attachments.of(content, type, fileName);
	}

	/**
	 * What users see for one of the modules' exceptions: its code's message, in their language.
	 */
	protected String message(JobWardenException exception) {
		String inEnglish = Objects.requireNonNullElse(exception.getMessage(), exception.getErrorCode().getCode());
		return this.messages.getMessage(exception.getErrorCode().getMessageKey(), exception.getArguments(), inEnglish,
				LocaleContextHolder.getLocale());
	}

	/**
	 * The first error of each field, by field name, in the user's language, to show next to the form's fields.
	 */
	protected Map<String, String> fieldErrors(BindingResult result) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : result.getFieldErrors()) {
			// The validation message, or for a value that couldn't even be converted (e.g. text as a date) the
			// typeMismatch message
			errors.putIfAbsent(error.getField(), this.messages.getMessage(error, LocaleContextHolder.getLocale()));
		}
		return errors;
	}

	/**
	 * All the form's errors as one text, for forms that show a single error line.
	 */
	protected String errorMessage(BindingResult result) {
		return String.join(" ", fieldErrors(result).values());
	}

}
