package com.jobwarden.web.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jobwarden.common.exception.JobWardenException;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.assistant.AssistantConnections;
import com.jobwarden.web.assistant.AssistantConnections.Connection;
import com.jobwarden.web.assistant.AssistantRunner;
import com.jobwarden.web.constants.AppConstants.HtmxHeaders;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.user.CurrentUser;
import com.jobwarden.web.user.ProfileNotSelectedException;
import com.jobwarden.web.view.AssistantPrompts;
import com.jobwarden.web.view.AssistantPrompts.Prompt;
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

	private @Nullable InboxService inbox;

	private @Nullable AssistantRunner runner;

	private @Nullable AssistantConnections connections;

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
	 * Optional, like the updates: without it, the sidebar counts no emails waiting.
	 */
	@Autowired(required = false)
	final void setInbox(InboxService inbox) {
		this.inbox = inbox;
	}

	/**
	 * Optional too: without it, the pages offer no Run, only the prompts to paste.
	 */
	@Autowired(required = false)
	final void setRunner(AssistantRunner runner) {
		this.runner = runner;
	}

	/**
	 * Optional too: without it, the pages open the apps without asking whether they're connected.
	 */
	@Autowired(required = false)
	final void setConnections(AssistantConnections connections) {
		this.connections = connections;
	}

	/**
	 * Spring calls this before every handler method of a subclass, so every page has the selected profile (or
	 * {@code null}) for the sidebar in layout.html. It's the only place a request looks the profile up.
	 * <p>
	 * The sidebar also lists the profiles to switch to, counts the profile's applications, the found jobs waiting on
	 * the AI Assistant page and the emails waiting in the inbox, and says when a newer JobWarden is out. HTMX requests skip those: they only swap a part
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
			if (this.inbox != null) {
				model.addAttribute(ModelAttributes.INBOX_COUNT, this.inbox.countPending(profile.id()));
			}
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
	 * What to paste into the profile's AI assistant to do a request, and the terminal command that starts the assistant
	 * with it; and whether JobWarden can run it itself, here, and does: the profile is the one {@link #layout} put in the
	 * model.
	 */
	protected Prompt prompt(Model model, long requestId) {
		return prompt(model, List.of(requestId));
	}

	/**
	 * The same for several requests, done one after the other in one session.
	 */
	protected Prompt prompt(Model model, List<Long> requestIds) {
		User profile = requireProfile(model);
		Prompt prompt = AssistantPrompts.forRequests(profile.assistant(), requestIds, profile.id());
		return connected((this.runner == null) ? prompt
				: prompt.run(this.runner.canRun(profile.assistant()), this.runner.isRunning(requestIds)));
	}

	/**
	 * Whether the assistant has JobWarden's MCP server on this computer; unknown without a way to tell.
	 */
	protected Connection connection(Assistant assistant) {
		Connection connection = (this.connections == null) ? null : this.connections.find(assistant);
		return (connection == null) ? Connection.UNKNOWN : connection;
	}

	/**
	 * The prompt, its apps knowing whether each assistant has JobWarden's MCP server on this computer: one that hasn't
	 * says to connect it first, in Settings.
	 */
	protected Prompt connected(Prompt prompt) {
		if (this.connections == null) {
			return prompt;
		}
		return prompt.withApps(prompt.apps().stream().map(link -> {
			Connection connection = connection(link.assistant());
			return link.checked(!connection.isMissing(), connection.folder());
		}).toList());
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

	/**
	 * Deletes the profile and everything of it (UserService.delete), stops the assistant's runs on its requests and
	 * deletes what they printed, and selects no profile in this browser session.
	 */
	protected void deleteProfile(long userId) {
		List<Long> requestIds = this.discovery.findRequests(userId, Integer.MAX_VALUE).stream().map(AiRequest::id).toList();
		if (this.runner != null) {
			this.runner.forget(requestIds);
		}
		this.users.delete(userId);
		this.currentUser.forget();
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
