package com.jobwarden.web.controller;

import java.time.Duration;

import com.jobwarden.identity.dto.User;
import com.jobwarden.web.constants.ModelAttributes;
import com.jobwarden.web.enums.JobsView;
import com.jobwarden.web.user.ProfileNotSelectedException;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;

/**
 * What the page controllers have in common and needs no bean, imported statically.
 */
final class Pages {

	private Pages() {
	}

	/**
	 * For pages that need a profile: the one {@link LayoutAdvice} put in the model.
	 *
	 * @throws ProfileNotSelectedException if none is selected, which sends the browser to the profile picker
	 */
	static User requireProfile(Model model) {
		if (model.getAttribute(ModelAttributes.CURRENT_PROFILE) instanceof User profile) {
			return profile;
		}
		throw new ProfileNotSelectedException();
	}

	static String redirectTo(String path) {
		return "redirect:" + path;
	}

	/**
	 * Keeps the Jobs page's view, the list or the board, for the sidebar's link to open next time.
	 */
	static void rememberJobsView(HttpServletResponse response, JobsView view) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JobsView.COOKIE, view.name())
			.path("/")
			.maxAge(Duration.ofDays(365))
			.httpOnly(true)
			.sameSite("Lax")
			.build()
			.toString());
	}

	/**
	 * A file to download, under its name, which may have any letters: browsers that only read ASCII names get one with
	 * the others left out.
	 */
	static ResponseEntity<byte[]> attachment(byte[] content, MediaType type, String fileName) {
		return Attachments.of(content, type, fileName);
	}

}
