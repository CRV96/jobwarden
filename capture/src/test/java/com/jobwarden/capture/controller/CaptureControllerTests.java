package com.jobwarden.capture.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(CaptureController.class)
class CaptureControllerTests {

	private static final String CAPTURE = """
			{
			  "userId": 1,
			  "action": "APPLY",
			  "title": "Backend engineer",
			  "company": "Acme",
			  "link": "https://www.linkedin.com/jobs/view/42",
			  "rawPayload": { "@type": "JobPosting" }
			}
			""";

	private static final String LINK = "https://www.linkedin.com/jobs/view/42";

	@Autowired
	private MockMvcTester mvc;

	@Value("${jobwarden.capture.path}")
	private String capturePath;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private UserService users;

	@Test
	void rejectsCaptureWithoutTitleAsProblemDetail() {
		assertThat(this.mvc.post()
			.uri(this.capturePath)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "userId": 1, "action": "APPLY", "link": "https://example.com/job" }
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.errorCode").isEqualTo("COMMON-002");
				assertThat(body).extractingPath("$.errors.title").isEqualTo("must not be blank");
			});
	}

	@Test
	void givesMalformedJsonAnErrorCodeToo() {
		assertThat(this.mvc.post().uri(this.capturePath).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("COMMON-002");
	}

	@Test
	void rejectsALinkThatIsNotAWebAddress() {
		assertThat(this.mvc.post()
			.uri(this.capturePath)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "userId": 1, "action": "APPLY", "title": "Backend engineer", "link": "not a link" }
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void rejectsAnUnknownProfile() {
		given(this.users.exists(1)).willReturn(false);

		assertThat(this.mvc.post().uri(this.capturePath).contentType(MediaType.APPLICATION_JSON).content(CAPTURE))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("IDENTITY-001");
	}

	@Test
	void answersInTheLanguageOfTheRequest() {
		given(this.users.exists(1)).willReturn(false);

		assertThat(this.mvc.post()
			.uri(this.capturePath)
			.header("Accept-Language", "ro-RO,ro;q=0.9")
			.contentType(MediaType.APPLICATION_JSON)
			.content(CAPTURE)).bodyJson().extractingPath("$.detail").isEqualTo("Profilul 1 nu există.");
	}

	@Test
	void tracksTheCapturedPosting() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.track(any())).willReturn(new TrackedApplication(7, true));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(application(ApplicationStatus.APPLIED)));

		assertThat(this.mvc.post().uri(this.capturePath).contentType(MediaType.APPLICATION_JSON).content(CAPTURE))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.applicationId").isEqualTo(7);
				assertThat(body).extractingPath("$.status").isEqualTo("APPLIED");
			});

		ArgumentCaptor<NewApplication> tracked = ArgumentCaptor.captor();
		then(this.applications).should().track(tracked.capture());
		assertThat(tracked.getValue()).satisfies(application -> {
			assertThat(application.title()).isEqualTo("Backend engineer");
			assertThat(application.link()).isEqualTo("https://www.linkedin.com/jobs/view/42");
			assertThat(application.rawPayload()).isEqualTo("{\"@type\":\"JobPosting\"}");
			assertThat(application.initialStatus()).isEqualTo(ApplicationStatus.APPLIED);
			assertThat(application.initialStatusDate()).isEqualTo(LocalDate.now());
			assertThat(application.initialStatusTime()).isNotNull();
		});
	}

	@Test
	void recordsOnlyTheDateWhenAskedTo() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.track(any())).willReturn(new TrackedApplication(7, true));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(application(ApplicationStatus.APPLIED)));

		assertThat(this.mvc.post()
			.uri(this.capturePath)
			.contentType(MediaType.APPLICATION_JSON)
			.content(CAPTURE.replace("\"userId\": 1,", "\"userId\": 1, \"includeTime\": false,")))
			.hasStatus(HttpStatus.CREATED);

		ArgumentCaptor<NewApplication> tracked = ArgumentCaptor.captor();
		then(this.applications).should().track(tracked.capture());
		assertThat(tracked.getValue().initialStatusDate()).isEqualTo(LocalDate.now());
		assertThat(tracked.getValue().initialStatusTime()).isNull();
	}

	@Test
	void answers200WithTheExistingApplicationForALinkAlreadyTracked() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.track(any())).willReturn(new TrackedApplication(7, false));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(application(ApplicationStatus.APPLIED)));

		assertThat(this.mvc.post().uri(this.capturePath).contentType(MediaType.APPLICATION_JSON).content(CAPTURE))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.applicationId")
			.isEqualTo(7);
	}

	@Test
	void answersWithTheApplicationTrackingALink() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.findByLink(1, LINK)).willReturn(Optional.of(application(ApplicationStatus.SAVED)));

		assertThat(this.mvc.get().uri(this.capturePath).param("userId", "1").param("link", LINK))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{
					  "applicationId": 7,
					  "title": "Backend engineer",
					  "company": "Acme",
					  "status": "SAVED",
					  "statusDate": "2026-09-28",
					  "statusTime": "20:31:05"
					}
					""");
	}

	@Test
	void answers204ForALinkNotTracked() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.findByLink(1, LINK)).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri(this.capturePath).param("userId", "1").param("link", LINK))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void looksUpOnlyForAKnownProfile() {
		given(this.users.exists(1)).willReturn(false);

		assertThat(this.mvc.get().uri(this.capturePath).param("userId", "1").param("link", LINK))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("IDENTITY-001");
	}

	@Test
	void movesAnApplicationBackToSaved() {
		given(this.users.exists(1)).willReturn(true);
		given(this.applications.findById(1, 7)).willReturn(Optional.of(application(ApplicationStatus.SAVED)));

		assertThat(this.mvc.post().uri(this.capturePath + "/7/move-back-to-saved").param("userId", "1"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("SAVED");
		then(this.applications).should().moveBackToSaved(1, 7);
	}

	@Test
	void saysWhyAnApplicationCannotMoveBack() {
		given(this.users.exists(1)).willReturn(true);
		willThrow(new BusinessRuleException(JobsErrorCode.CANNOT_MOVE_BACK_TO_SAVED, "Application 7 is INTERVIEWING"))
			.given(this.applications)
			.moveBackToSaved(1, 7);

		assertThat(this.mvc.post().uri(this.capturePath + "/7/move-back-to-saved").param("userId", "1"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("JOBS-003");
	}

	@Test
	void removesAnApplication() {
		given(this.users.exists(1)).willReturn(true);

		assertThat(this.mvc.delete().uri(this.capturePath + "/7").param("userId", "1"))
			.hasStatus(HttpStatus.NO_CONTENT);
		then(this.applications).should().delete(1, 7);
	}

	@Test
	void answers404ForRemovingAnApplicationThatIsGone() {
		given(this.users.exists(1)).willReturn(true);
		willThrow(new ApplicationNotFoundException(7)).given(this.applications).delete(1, 7);

		assertThat(this.mvc.delete().uri(this.capturePath + "/7").param("userId", "1"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("JOBS-001");
	}

	/**
	 * Application 7, whose status changed on 28 September 2026 at 20:31:05.
	 */
	private static ApplicationDetails application(ApplicationStatus status) {
		return ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company("Acme")
			.link(LINK)
			.status(status)
			.capturedAt(Instant.now())
			.timeline(List.of(event(1, ApplicationStatus.SAVED, null),
					event(2, status, LocalTime.of(20, 31, 5))))
			.build();
	}

	private static TimelineEvent event(long id, ApplicationStatus stageCategory, @Nullable LocalTime time) {
		return TimelineEvent.builder()
			.id(id)
			.eventDate(LocalDate.of(2026, 9, 28))
			.eventTime(time)
			.stageCategory(stageCategory)
			.build();
	}

}
