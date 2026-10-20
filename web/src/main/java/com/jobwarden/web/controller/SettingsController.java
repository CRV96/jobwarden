package com.jobwarden.web.controller;

import java.util.Locale;

import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.constants.AppConstants.ModelAttributes;
import com.jobwarden.web.constants.AppConstants.Views;
import com.jobwarden.web.enums.Language;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Settings of this browser, not of a profile, so the page needs none: the theme (kept by the browser), the language
 * (kept in a cookie, see LocaleConfiguration) and what the extension needs to know.
 */
@Controller
@RequestMapping(ControllerConstants.SETTINGS)
class SettingsController extends BaseController {

	@GetMapping
	String settings(Model model, Locale locale) {
		model.addAttribute(ModelAttributes.LANGUAGES, Language.values());
		model.addAttribute(ModelAttributes.CURRENT_LANGUAGE,
				Language.fromTag(locale.toLanguageTag()).orElse(Language.DEFAULT));
		model.addAttribute(ModelAttributes.SERVER_URL, ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
		return Views.SETTINGS;
	}

}
