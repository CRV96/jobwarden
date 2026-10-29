package com.jobwarden.web.controller;

import java.util.LinkedHashSet;
import java.util.Locale;

import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.service.AdzunaKeyService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.dto.AdzunaKeyForm;
import com.jobwarden.web.enums.Language;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Settings of this browser, not of a profile, so the page needs none: the theme (kept by the browser), the language
 * (kept in a cookie, see LocaleConfiguration), and what the extension and Claude need to know. And one of the server,
 * the same for every profile: the key for Adzuna's API, which the Salaries page asks for the salaries in job ads.
 */
@Controller
@RequestMapping(ControllerConstants.SETTINGS)
class SettingsController extends BaseController {

	/** Where the page shows the Adzuna key */
	private static final String SALARIES_SECTION = ControllerConstants.SETTINGS + "#salaries";

	private final AdzunaKeyService adzunaKeys;

	private final String mcpPath;

	SettingsController(AdzunaKeyService adzunaKeys, @Value("${jobwarden.mcp.path:/mcp}") String mcpPath) {
		this.adzunaKeys = adzunaKeys;
		this.mcpPath = mcpPath;
	}

	@GetMapping
	String settings(Model model, Locale locale) {
		model.addAttribute(ModelAttributes.LANGUAGES, Language.values());
		model.addAttribute(ModelAttributes.CURRENT_LANGUAGE,
				Language.fromTag(locale.toLanguageTag()).orElse(Language.DEFAULT));
		String serverUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
		model.addAttribute(ModelAttributes.SERVER_URL, serverUrl);
		model.addAttribute(ModelAttributes.MCP_URL, serverUrl + this.mcpPath);
		model.addAttribute(ModelAttributes.ADZUNA_KEY, this.adzunaKeys.find());
		return Views.SETTINGS;
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
		AdzunaKeyCheck check = this.adzunaKeys.save(form.appId(), form.appKey());
		redirect.addFlashAttribute(ModelAttributes.ADZUNA_CHECK, check);
		if (check == AdzunaKeyCheck.REFUSED) {
			redirect.addFlashAttribute(ModelAttributes.ADZUNA_APP_ID, form.appId().strip());
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
