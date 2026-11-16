package com.jobwarden.capture.controller;

import com.jobwarden.capture.dto.ProfileView;
import com.jobwarden.identity.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which profile an id names, for the extension's settings: the profile's public id, as Settings shows it, or its id,
 * as given before. The extension keeps the number it answers with, and sends it with its captures. Next to the
 * captures, so the extension reaches it like them (see {@link CaptureController}).
 */
@RestController("captureProfileController")
@RequestMapping("${jobwarden.capture.path}/profile")
@RequiredArgsConstructor
class ProfileController {

	private final UserService users;

	/**
	 * @return 200 with the profile, or 404 when the id names none
	 */
	@GetMapping
	ResponseEntity<ProfileView> find(@RequestParam String key) {
		return this.users.findByKey(key)
			.map(user -> new ProfileView(user.id(), user.publicId(), user.name()))
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
