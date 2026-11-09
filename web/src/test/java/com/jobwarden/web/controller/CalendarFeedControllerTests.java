package com.jobwarden.web.controller;

import java.util.List;
import java.util.Optional;

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
			.hasBodyTextEqualTo("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//JobWarden//Calendar//EN\r\nCALSCALE:GREGORIAN\r\n"
					+ "METHOD:PUBLISH\r\nX-WR-CALNAME:JobWarden\r\nREFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n"
					+ "X-PUBLISHED-TTL:PT1H\r\nEND:VCALENDAR\r\n");
		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=s3cret&download=true")).headers()
			.hasValue("Content-Disposition", "attachment; filename=\"jobwarden.ics\"");
	}

	@Test
	void givesNothingForASecretThatIsntAProfiles() {
		given(this.feeds.findOwner("guess")).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/calendar/feed.ics?token=guess")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
