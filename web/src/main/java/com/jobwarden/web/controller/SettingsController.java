package com.jobwarden.web.controller;

import java.util.Locale;

import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.enums.Language;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Settings of this browser, not of a profile, so the page needs none: the theme (kept by the browser), the language
 * (kept in a cookie, see LocaleConfiguration), and what the extension and Claude need to know.
 */
@Controller
@RequestMapping(ControllerConstants.SETTINGS)
class SettingsController extends BaseController {

	private final String mcpPath;

	SettingsController(@Value("${jobwarden.mcp.path:/mcp}") String mcpPath) {
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
		return Views.SETTINGS;
	}

}
