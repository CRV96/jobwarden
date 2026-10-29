package com.jobwarden.capture.controller;

import com.jobwarden.capture.dto.ServerVersion;
import com.jobwarden.capture.dto.ServerVersion.LatestRelease;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The versions, for the extension's popup: the server's, and the latest released. Next to the captures, so the
 * extension reaches it like them, from Firefox and Chrome (see {@link CaptureController}).
 */
@RestController
@RequestMapping("${jobwarden.capture.path}/version")
@CrossOrigin(originPatterns = { "moz-extension://*", "chrome-extension://*" })
@RequiredArgsConstructor
class VersionController {

	private final UpdateService updates;

	@GetMapping
	ServerVersion version() {
		UpdateStatus status = this.updates.status();
		Release latest = status.latest();
		return new ServerVersion(status.version(), (latest != null)
				? new LatestRelease(latest.version(), latest.name(), latest.url(), latest.publishedAt()) : null,
				status.releases());
	}

}
