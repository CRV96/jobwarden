package com.jobwarden.web.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.jobs.dto.CalendarEntry;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CalendarFeedService;
import com.jobwarden.web.view.CalendarIcs;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * No session: calendar apps ask with the address's secret alone.
 */
@WebMvcTest(CalendarFeedController.class)
@Import(CalendarIcs.class)
class CalendarFeedControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CalendarFeedService feeds;

	@MockitoBean
	private ApplicationService applications;

	@Test
	void givesTheProfilesCalendarForItsSecret() {
		given(this.feeds.findOwner("s3cret")).willReturn(Optional.of(1L));
		given(this.applications.findCalendar(eq(1L), any(), any())).willReturn(List.of());

		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=s3cret")).hasStatusOk()
			.hasContentTypeCompatibleWith("text/calendar")
			.hasBodyTextEqualTo("""
					BEGIN:VCALENDAR\r
					VERSION:2.0\r
					PRODID:-//JobWarden//Calendar//EN\r
					CALSCALE:GREGORIAN\r
					METHOD:PUBLISH\r
					X-WR-CALNAME:JobWarden\r
					REFRESH-INTERVAL;VALUE=DURATION:PT1H\r
					X-PUBLISHED-TTL:PT1H\r
					END:VCALENDAR\r
					""");
		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=s3cret&download=true")).headers()
			.hasValue("Content-Disposition", "attachment; filename=\"jobwarden.ics\"");
	}

	@Test
	void givesOnlyWhatTheAddressAsksFor() {
		given(this.feeds.findOwner("s3cret")).willReturn(Optional.of(1L));
		given(this.applications.findCalendar(eq(1L), any(), any())).willReturn(List.of(CalendarEntry.builder()
			.date(LocalDate.now().plusDays(2))
			.kind(CalendarEntryKind.FOLLOW_UP)
			.applicationId(7)
			.title("Backend engineer")
			.status(ApplicationStatus.APPLIED)
			.build(), CalendarEntry.builder()
				.date(LocalDate.now().plusDays(3))
				.kind(CalendarEntryKind.DEADLINE)
				.applicationId(8)
				.title("Data engineer")
				.status(ApplicationStatus.SAVED)
				.build()));

		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=s3cret&show=follow-up")).hasStatusOk()
			.bodyText()
			.contains("UID:follow-up-7@jobwarden", "X-WR-CALNAME:JobWarden · Follow-ups")
			.doesNotContain("deadline-8");
		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=s3cret")).hasStatusOk()
			.bodyText()
			.contains("UID:follow-up-7@jobwarden", "UID:deadline-8@jobwarden");
	}

	@Test
	void givesNothingForASecretThatIsntAProfiles() {
		given(this.feeds.findOwner("guess")).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=guess")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
