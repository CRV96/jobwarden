package com.jobwarden.capture.controller;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.jobwarden.capture.dto.CaptureRequest;
import com.jobwarden.capture.dto.CapturedApplication;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The extension's API. It saves captures in the jobs module and looks up, moves back or removes what it saved.
 * Every answer about an application is a {@link CapturedApplication}, so the extension always shows its current state.
 */
@RestController
@RequestMapping("${jobwarden.capture.path}")
@CustomLog
@RequiredArgsConstructor
class CaptureController {

	private final ApplicationService applications;

	private final UserService users;

	private final JsonMapper jsonMapper;

	private final LogoDownloader logos;

	/**
	 * What the profile has for a link: the extension looks up the page it's on when it opens.
	 * @return 200 with the application, or 204 if the profile doesn't track the link
	 */
	@GetMapping
	ResponseEntity<CapturedApplication> find(@RequestParam long userId, @RequestParam String link) {
		requireProfile(userId);
		return this.applications.findByLink(userId, link)
			.map(CaptureController::toCapturedApplication)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/**
	 * Saves a link to the profile.
	 * @return 201 with the new application, or 200 with the existing one
	 */
	@PostMapping
	ResponseEntity<CapturedApplication> capture(@Valid @RequestBody CaptureRequest request) {
		requireProfile(request.userId());
		TrackedApplication application = this.applications.track(toNewApplication(request));

		log.debug("Captured {} for profile {}: application {} ({})", request.action(), request.userId(),
				application.id(), application.created() ? "new" : "already tracked");

		return ResponseEntity.status(application.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(captured(request.userId(), application.id()));
	}

	/**
	 * Takes back "I applied": the extension's Move back to bookmarks.
	 */
	@PostMapping("/{applicationId}/move-back-to-saved")
	CapturedApplication moveBackToSaved(@PathVariable long applicationId, @RequestParam long userId) {
		requireProfile(userId);
		this.applications.moveBackToSaved(userId, applicationId);
		return captured(userId, applicationId);
	}

	/**
	 * Removes the application with its timeline: the extension's Remove bookmark and Remove application.
	 */
	@DeleteMapping("/{applicationId}")
	ResponseEntity<Void> remove(@PathVariable long applicationId, @RequestParam long userId) {
		requireProfile(userId);
		this.applications.delete(userId, applicationId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * The extension has the profile id from its options, where it may be out of date: this tells the user to fix it.
	 */
	private void requireProfile(long userId) {
		if (!this.users.exists(userId)) {
			throw new UserNotFoundException(userId);
		}
	}

	private CapturedApplication captured(long userId, long applicationId) {
		return this.applications.findById(userId, applicationId)
			.map(CaptureController::toCapturedApplication)
			.orElseThrow(() -> new ApplicationNotFoundException(applicationId));
	}

	private NewApplication toNewApplication(CaptureRequest request) {
		// In the server's time zone, like every date and time the pages show
		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		boolean includeTime = !Boolean.FALSE.equals(request.includeTime());
		return NewApplication.builder()
			.userId(request.userId())
			.title(request.title())
			.company(request.company())
			.description(request.description())
			.link(request.link())
			.location(request.location())
			.salary(request.salary())
			.employmentType(request.employmentType())
			.rawPayload(toJson(request.rawPayload()))
			.initialStatus(request.action().getInitialStatus())
			.initialStatusDate(now.toLocalDate())
			.initialStatusTime(includeTime ? now.toLocalTime() : null)
			.sourceIcon(request.toSourceIcon())
			// Before the application is saved, outside its transaction: downloading can take a few seconds
			.companyLogo(this.logos.download(request.logoUrl()).orElse(null))
			.build();
	}

	private @Nullable String toJson(@Nullable Map<String, Object> rawPayload) {
		return (rawPayload != null) ? this.jsonMapper.writeValueAsString(rawPayload) : null;
	}

	/**
	 * The status changed at the latest event: the status follows it. Every timeline has one, its first event.
	 */
	private static CapturedApplication toCapturedApplication(ApplicationDetails application) {
		TimelineEvent latest = application.timeline().getLast();
		return CapturedApplication.builder()
			.applicationId(application.id())
			.title(application.title())
			.company(application.company())
			.status(application.status())
			.statusDate(latest.eventDate())
			.statusTime(latest.eventTime())
			.build();
	}

}
