package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.Locale;

import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.constants.ControllerConstants;
import com.jobwarden.web.user.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.context.MessageSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How many jobs the profile has, and when they last changed, for app.js: every page asks every few seconds while it's
 * in front, and at once when it comes back in front, so the sidebar's count, and the Jobs page, follow what the browser
 * extension saves from another tab. Not a page: no sidebar to fill in, so it skips {@link LayoutAdvice}, and answers
 * with nothing when no profile is selected.
 */
@RestController
@RequiredArgsConstructor
class JobChangesController {

	private final CurrentUser currentUser;

	private final ApplicationService applications;

	private final MessageSource messages;

	@GetMapping(ControllerConstants.APPLICATIONS + "/changes")
	ResponseEntity<Changes> changes(Locale locale) {
		return this.currentUser.get().map(profile -> {
			long count = this.applications.count(profile.id());
			Instant latest = this.applications.find(profile.id(), ApplicationQuery.builder()
				.sort(ApplicationSort.MODIFIED)
				.direction(Sort.Direction.DESC)
				.page(0)
				.size(1)
				.build()).stream().findFirst().map(ApplicationSummary::lastModified).orElse(null);
			String subtitle = this.messages.getMessage("jobs.subtitle", new Object[] { count }, locale);
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Changes(count, version(count, latest), subtitle));
		}).orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** What changes whenever a job is added, deleted, archived or moves on: the count, and the latest change. */
	static String version(long count, @Nullable Instant latest) {
		return count + "@" + ((latest != null) ? latest.toEpochMilli() : 0);
	}

	/**
	 * @param count every job of the profile's, as the sidebar counts them
	 * @param version tells one state of the jobs from another, see {@link #version}
	 * @param subtitle the Jobs page's subtitle, which says how many
	 */
	record Changes(long count, String version, String subtitle) {
	}

}
