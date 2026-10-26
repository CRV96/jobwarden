package com.jobwarden.web.controller;

import java.time.Duration;
import java.util.Optional;

import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.user.CurrentUser;
import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The images the pages show: the icons of the sites postings came from ({@code /sources/linkedin.com/icon}) and the
 * companies' logos ({@code /applications/7/logo}). Images, not pages, so it doesn't extend BaseController, which would
 * look up the sidebar's profiles for every image. Only the selected profile's; 404 for anything else.
 */
@Controller
@RequiredArgsConstructor
class ImageController {

	/** Icons and logos rarely change: for a day, a list doesn't ask again for each row. */
	private static final Duration MAX_AGE = Duration.ofDays(1);

	private final ApplicationService applications;

	private final CurrentUser currentUser;

	@GetMapping(ControllerConstants.SOURCES + "/{source}/icon")
	ResponseEntity<byte[]> icon(@PathVariable String source) {
		return image(this.currentUser.get().flatMap(profile -> this.applications.findSourceIcon(profile.id(), source)));
	}

	@GetMapping(ControllerConstants.APPLICATIONS + "/{id}/logo")
	ResponseEntity<byte[]> logo(@PathVariable long id) {
		return image(this.currentUser.get().flatMap(profile -> this.applications.findCompanyLogo(profile.id(), id)));
	}

	private static ResponseEntity<byte[]> image(Optional<PngImage> image) {
		return image
			.map(png -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.maxAge(MAX_AGE).cachePrivate())
				.body(png.png()))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
