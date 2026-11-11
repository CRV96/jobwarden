package com.jobwarden.web.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CalendarFeedService;
import com.jobwarden.web.constants.AppConstants.ControllerConstants;
import com.jobwarden.web.enums.CalendarEntryType;
import com.jobwarden.web.view.CalendarIcs;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The calendar as a feed calendar apps subscribe to (Apple Calendar, Outlook, Google Calendar...): they ask with no
 * session, so the address's secret says whose calendar it is. Not a page: it skips {@link BaseController}.
 */
@Controller
@CustomLog
@RequiredArgsConstructor
class CalendarFeedController {

	private final CalendarFeedService feeds;

	private final ApplicationService applications;

	private final CalendarIcs ics;

	/**
	 * @param token the profile's secret, from the address the calendar page gives
	 * @param download true to save it as a file, e.g. to import it once into a calendar that can't subscribe
	 * @param show what to put in, as the calendar's legend names it ({@code interview}, {@code status},
	 * {@code deadline}, {@code follow-up}); {@link CalendarEntryType#FEED} when it names none
	 * @return 404 for a secret that isn't, or no longer is, a profile's
	 */
	@GetMapping(ControllerConstants.CALENDAR + "/feed.ics")
	ResponseEntity<String> feed(@RequestParam String token, @RequestParam(defaultValue = "false") boolean download,
			@RequestParam(required = false) @Nullable List<String> show, Locale locale) {
		Optional<Long> owner = this.feeds.findOwner(token);
		if (owner.isEmpty()) {
			log.debug("A calendar feed asked for with an unknown secret");
			return ResponseEntity.notFound().build();
		}
		LocalDate today = LocalDate.now();
		Set<CalendarEntryType> types = CalendarEntryType.parse((show != null) ? show : List.of());
		String calendar = this.ics.write(
				this.applications.findCalendar(owner.get(), today.minusDays(CalendarIcs.DAYS_BEFORE),
						today.plusDays(CalendarIcs.DAYS_AFTER)),
				types.isEmpty() ? CalendarEntryType.FEED : types,
				id -> ServletUriComponentsBuilder.fromCurrentContextPath()
					.path(ControllerConstants.APPLICATIONS + "/{id}")
					.buildAndExpand(id)
					.toUriString(),
				ZoneId.systemDefault(), locale);
		log.debug("Calendar feed of profile {} read", owner.get());
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
			.filename("jobwarden.ics")
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(CalendarIcs.MEDIA_TYPE))
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.body(calendar);
	}

}
