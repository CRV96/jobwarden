package com.jobwarden.web.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.service.AdzunaKeyService;
import com.jobwarden.web.assistant.AssistantConnections.Connection;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.AdzunaKeyForm;
import com.jobwarden.web.dto.InboxSettingsForm;
import com.jobwarden.web.enums.Language;
import com.jobwarden.web.enums.ReadingInterval;
import com.jobwarden.web.view.AssistantPrompts;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Settings of this browser, not of a profile, so the page needs none: the theme (kept by the browser), the language
 * (kept in a cookie, see LocaleConfiguration), and what the extension and the AI assistant need to know. One of the
 * server, the same for every profile: the key for Adzuna's API, which the Salaries page asks for the salaries in job
 * ads. And, when a profile is chosen, its own: its jobs as a CSV file, how its inbox reads its mailbox, and which AI
 * assistant it works with, Claude Code or Codex.
 */
@Controller
@RequestMapping(ControllerConstants.SETTINGS)
@RequiredArgsConstructor
class SettingsController extends BaseController {

	/** Where the page shows the Adzuna key */
	private static final String SALARIES_SECTION = ControllerConstants.SETTINGS + "#salaries";

	/** Where the page shows the inbox's settings */
	private static final String INBOX_SECTION = ControllerConstants.SETTINGS + "#inbox";

	/** Where the page shows the AI assistant, and how to connect it */
	private static final String ASSISTANT_SECTION = ControllerConstants.SETTINGS + "#assistant";

	/** Who made JobWarden, by their GitHub name: About names them, with a link to their GitHub profile. */
	static final String AUTHOR = "CRV";

	private final AdzunaKeyService adzunaKeys;

	private final InboxService inbox;

	private final UserService users;

	private final Clock clock;

	@Value("${jobwarden.mcp.path:/mcp}")
	private final String mcpPath;

	/** Where JobWarden's code is on GitHub, the repository its releases come from, e.g. CRV96/jobwarden */
	@Value("${jobwarden.updates.repository:CRV96/jobwarden}")
	private final String repository;

	@GetMapping
	String settings(Model model, Locale locale) {
		model.addAttribute(ModelAttributes.TODAY, LocalDate.now(this.clock));
		model.addAttribute(ModelAttributes.LANGUAGES, Language.values());
		model.addAttribute(ModelAttributes.CURRENT_LANGUAGE,
				Language.fromTag(locale.toLanguageTag()).orElse(Language.DEFAULT));
		String serverUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
		model.addAttribute(ModelAttributes.SERVER_URL, serverUrl);
		String mcpUrl = serverUrl + this.mcpPath;
		model.addAttribute(ModelAttributes.MCP_URL, mcpUrl);
		model.addAttribute(ModelAttributes.ASSISTANTS, Assistant.values());
		// How to connect each assistant: the profile may work with both
		String profileKey = (model.getAttribute(ModelAttributes.CURRENT_PROFILE) instanceof User profile)
				? profile.publicId().toString() : null;
		Map<Assistant, String> connections = new EnumMap<>(Assistant.class);
		for (Assistant assistant : Assistant.values()) {
			connections.put(assistant, AssistantPrompts.connection(assistant, mcpUrl, profileKey));
		}
		model.addAttribute(ModelAttributes.ASSISTANT_CONNECTION, connections);
		// Whether each has it already, from its own configuration on this computer
		Map<Assistant, Connection> states = new EnumMap<>(Assistant.class);
		for (Assistant assistant : Assistant.values()) {
			states.put(assistant, connection(assistant));
		}
		model.addAttribute(ModelAttributes.ASSISTANT_STATES, states);
		model.addAttribute(ModelAttributes.ADZUNA_KEY, this.adzunaKeys.find());
		model.addAttribute(ModelAttributes.REPOSITORY, this.repository);
		model.addAttribute(ModelAttributes.AUTHOR, AUTHOR);
		// The repository's owner, e.g. CRV96 of CRV96/jobwarden
		model.addAttribute(ModelAttributes.AUTHOR_URL, "https://github.com/" + this.repository.split("/")[0]);
		model.addAttribute(ModelAttributes.PROFILE_IMAGE_MAX_BYTES, UserService.MAX_IMAGE_BYTES);
		if (model.getAttribute(ModelAttributes.CURRENT_PROFILE) instanceof User profile) {
			Optional<MailAccount> mailbox = this.inbox.findAccount(profile.id());
			model.addAttribute(ModelAttributes.MAIL_ACCOUNT, mailbox.orElse(null));
			model.addAttribute(ModelAttributes.READING_INTERVAL,
					mailbox.map(account -> ReadingInterval.of(account.readEveryMinutes())).orElse(null));
			model.addAttribute(ModelAttributes.READING_INTERVALS, ReadingInterval.values());
			model.addAttribute(ModelAttributes.INBOX_AUTOMATIONS, InboxAutomation.values());
		}
		return Views.SETTINGS;
	}

	/**
	 * How often the profile's mailbox is read, and what becomes of the updates found: those waiting follow the new
	 * choice at once. Back on the page, it says so once.
	 */
	@PostMapping("/inbox")
	String saveInbox(@Valid InboxSettingsForm form, BindingResult result, Model model, RedirectAttributes redirect) {
		long userId = requireProfile(model).id();
		if (result.hasErrors() || form.readEvery() == null || form.automation() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inbox settings without a choice");
		}
		this.inbox.changeSettings(userId, form.readEvery().getMinutes(), form.automation());
		redirect.addFlashAttribute(ModelAttributes.INBOX_SAVED, true);
		return redirectTo(INBOX_SECTION);
	}

	/**
	 * Which AI assistant the profile works with: its pages write their prompts and terminal commands for it from now
	 * on. Back on the page, it says so once, with how to connect that one.
	 */
	@PostMapping("/assistant")
	String saveAssistant(@RequestParam Assistant assistant, Model model, RedirectAttributes redirect) {
		this.users.changeAssistant(requireProfile(model).id(), assistant);
		redirect.addFlashAttribute(ModelAttributes.ASSISTANT_SAVED, true);
		return redirectTo(ASSISTANT_SECTION);
	}

	/**
	 * Saves the key unless Adzuna turns it down, and says how it went once, back on the page. The app key never goes
	 * back to the page: after an error, only the app ID is filled in again.
	 */
	@PostMapping("/adzuna")
	String saveAdzunaKey(@Valid AdzunaKeyForm form, BindingResult result, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			// Both fields may break the same rule
			redirect.addFlashAttribute(ModelAttributes.ADZUNA_ERROR,
					String.join(" ", new LinkedHashSet<>(fieldErrors(result).values())));
			redirect.addFlashAttribute(ModelAttributes.ADZUNA_APP_ID, form.appId());
			return redirectTo(SALARIES_SECTION);
		}
		AdzunaKeyCheck check = this.adzunaKeys.save(form.requiredAppId(), form.requiredAppKey());
		redirect.addFlashAttribute(ModelAttributes.ADZUNA_CHECK, check);
		if (check == AdzunaKeyCheck.REFUSED) {
			redirect.addFlashAttribute(ModelAttributes.ADZUNA_APP_ID, form.requiredAppId().strip());
		}
		return redirectTo(SALARIES_SECTION);
	}

	/**
	 * Back to the environment's key, if the server has one.
	 */
	@PostMapping("/adzuna/remove")
	String removeAdzunaKey(RedirectAttributes redirect) {
		this.adzunaKeys.remove();
		redirect.addFlashAttribute(ModelAttributes.ADZUNA_REMOVED, true);
		return redirectTo(SALARIES_SECTION);
	}

}
