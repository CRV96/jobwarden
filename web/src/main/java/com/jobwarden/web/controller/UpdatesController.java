package com.jobwarden.web.controller;

import java.util.List;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.constants.Views;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What's new in JobWarden: each release GitHub lists, with what it changed, the newest first; and the popup a profile
 * sees once after the server was updated (whats-new.js), with the releases since the version it saw last. Neither
 * waits for GitHub: they show what the updates module last heard.
 */
@PageController
@RequiredArgsConstructor
class UpdatesController {

	private final UpdateService updates;

	@GetMapping(ControllerConstants.UPDATES)
	String updates(Model model) {
		model.addAttribute(ModelAttributes.RELEASES, this.updates.releases());
		return Views.UPDATES;
	}

	/**
	 * @param since the version the profile saw before, as this browser remembers it; none when it remembers none
	 * @return the popup; nothing (204) when the version running isn't a release GitHub lists, or GitHub hasn't answered
	 * yet: the page asks again on the next one
	 */
	@GetMapping(ControllerConstants.WHATS_NEW)
	@Nullable String whatsNew(@RequestParam(required = false) @Nullable String since, Model model,
			HttpServletResponse response) {
		List<Release> releases = this.updates.whatsNew(since);
		if (releases.isEmpty()) {
			// With the response a parameter, a null answer is the response as it is
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return null;
		}
		model.addAttribute(ModelAttributes.WHATS_NEW, releases);
		model.addAttribute(ModelAttributes.WHATS_NEW_SINCE, (since != null && !since.isBlank()) ? since : null);
		return Views.WHATS_NEW_FRAGMENT;
	}
}
