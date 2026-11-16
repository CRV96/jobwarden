package com.jobwarden.capture.controller;

import java.util.List;
import java.util.Locale;

import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ReminderNotice;
import com.jobwarden.jobs.enums.CalendarEntryKind;
import com.jobwarden.jobs.service.ReminderService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(RemindersController.class)
class RemindersControllerTests {

	private static final String CHROME_EXTENSION = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

	@Autowired
	private MockMvcTester mvc;

	@Value("${jobwarden.capture.path}")
	private String capturePath;

	@MockitoBean
	private ReminderService reminders;

	@MockitoBean
	private UserService users;

	@Test
	void givesTheRemindersDueInTheExtensionsLanguage() {
		given(this.users.exists(1)).willReturn(true);
		given(this.reminders.claimDue(1, Locale.forLanguageTag("ro"))).willReturn(List.of(new ReminderNotice(5, 7,
				CalendarEntryKind.FOLLOW_UP, "Revino · mâine", "Java Developer la Acme")));

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/reminders/due")
			.param("userId", "1")
			.header(HttpHeaders.ORIGIN, CHROME_EXTENSION)
			.header(HttpHeaders.ACCEPT_LANGUAGE, "ro"))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CHROME_EXTENSION)
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"id": 5, "applicationId": 7, "title": "Revino · mâine", "body": "Java Developer la Acme"}]
					""");
	}

	@Test
	void refusesAProfileThatDoesntExist() {
		assertThat(this.mvc.post().uri(this.capturePath + "/reminders/due").param("userId", "9"))
			.hasStatus(HttpStatus.NOT_FOUND);
		then(this.reminders).should(never()).claimDue(anyLong(), any());
	}

	@Test
	void refusesAWebPage() {
		given(this.users.exists(1)).willReturn(true);

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/reminders/due")
			.param("userId", "1")
			.header(HttpHeaders.ORIGIN, "https://evil.example.com"))
			.hasStatus(HttpStatus.FORBIDDEN);
		then(this.reminders).should(never()).claimDue(anyLong(), any());
	}

}
