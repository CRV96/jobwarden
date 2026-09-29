package com.jobwarden.jobs;

import java.time.LocalDate;
import java.time.LocalTime;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.dto.TrackedApplication;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.ApplicationNotFoundException;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.service.ApplicationService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The jobs module starts here without identity, so the profiles that own the applications are inserted with SQL.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ApplicationServiceTests {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private ApplicationService applications;

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long otherUserId;

	@BeforeEach
	void createProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
		this.userId = insertProfile("Robert");
		this.otherUserId = insertProfile("Someone else");
	}

	@Test
	void startsTheTimelineWithTheInitialStatus() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.APPLIED);

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.title()).isEqualTo("Backend engineer");
		assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
		assertThat(application.capturedAt()).isNotNull();
		assertThat(application.timeline()).singleElement().satisfies(event -> {
			assertThat(event.eventDate()).isEqualTo(TODAY);
			assertThat(event.eventTime()).isNull();
			assertThat(event.stageCategory()).isEqualTo(ApplicationStatus.APPLIED);
		});
	}

	@Test
	void startsTheTimelineAtTheGivenDateAndTime() {
		long id = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend engineer")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(TODAY.minusDays(10))
			.initialStatusTime(LocalTime.of(9, 30))
			.build());

		assertThat(this.applications.findById(this.userId, id).orElseThrow().timeline()).singleElement()
			.satisfies(event -> {
				assertThat(event.eventDate()).isEqualTo(TODAY.minusDays(10));
				assertThat(event.eventTime()).isEqualTo(LocalTime.of(9, 30));
			});
	}

	@Test
	void takesTheSourceFromTheLink() {
		long fromLinkedIn = this.applications.create(newApplication(this.userId, "From LinkedIn",
				ApplicationStatus.SAVED, "https://www.linkedin.com/jobs/view/42"));
		long referral = this.applications.create(newApplication(this.userId, "Referral", ApplicationStatus.APPLIED,
				null));

		assertThat(this.applications.findById(this.userId, fromLinkedIn).orElseThrow().source())
			.isEqualTo("linkedin.com");
		assertThat(this.applications.findById(this.userId, referral).orElseThrow()).satisfies(application -> {
			assertThat(application.link()).isNull();
			assertThat(application.source()).isNull();
		});
	}

	@Test
	void storesTheRawPayloadAsJson() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.SAVED);

		assertThat(this.jdbc.sql("SELECT raw_payload ->> '@type' FROM applications WHERE id = ?")
			.param(id)
			.query(String.class)
			.single()).isEqualTo("JobPosting");
	}

	@Test
	void listsNewestFirstAndFiltersByStatus() {
		long first = create(this.userId, "First", ApplicationStatus.SAVED);
		long second = create(this.userId, "Second", ApplicationStatus.APPLIED);
		long third = create(this.userId, "Third", ApplicationStatus.SAVED);

		assertThat(this.applications.findAll(this.userId, null)).extracting(ApplicationSummary::id)
			.containsExactly(third, second, first);
		assertThat(this.applications.findAll(this.userId, ApplicationStatus.SAVED)).extracting(ApplicationSummary::id)
			.containsExactly(third, first);
	}

	@Test
	void keepsEachProfilesApplicationsToItself() {
		long othersApplication = create(this.otherUserId, "Not yours", ApplicationStatus.APPLIED);
		NewTimelineEvent rejection = event(TODAY, ApplicationStatus.REJECTED);

		assertThat(this.applications.findAll(this.userId, null)).isEmpty();
		assertThat(this.applications.findById(this.userId, othersApplication)).isEmpty();
		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.applications.addEvent(this.userId, othersApplication, rejection));
		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.applications.delete(this.userId, othersApplication));
		assertThat(this.applications.findById(this.otherUserId, othersApplication)).get()
			.extracting(ApplicationDetails::status)
			.isEqualTo(ApplicationStatus.APPLIED);
	}

	@Test
	void statusFollowsTheLatestEvent() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.APPLIED);

		this.applications.addEvent(this.userId, id, event(TODAY.plusDays(2), ApplicationStatus.INTERVIEWING));
		// Happened before the latest event: it joins the timeline but doesn't change the status
		this.applications.addEvent(this.userId, id, event(TODAY.minusDays(3), ApplicationStatus.SAVED));

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.INTERVIEWING);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory)
			.containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEWING);
	}

	@Test
	void eventAddedLastAtTheSameMomentIsTheLatest() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.SAVED);

		this.applications.addEvent(this.userId, id, event(TODAY, ApplicationStatus.APPLIED));

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory)
			.containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
	}

	@Test
	void ordersEachDayByTimeWithTheEventsWithoutATimeFirst() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.SAVED);

		this.applications.addEvent(this.userId, id, event(TODAY, LocalTime.of(15, 0), ApplicationStatus.INTERVIEWING));
		this.applications.addEvent(this.userId, id, event(TODAY, LocalTime.of(9, 0), ApplicationStatus.APPLIED));

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.INTERVIEWING);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory, TimelineEvent::eventTime)
			.containsExactly(tuple(ApplicationStatus.SAVED, null), tuple(ApplicationStatus.APPLIED, LocalTime.of(9, 0)),
					tuple(ApplicationStatus.INTERVIEWING, LocalTime.of(15, 0)));
	}

	@Test
	void allowsARejectionReasonOnlyOnARejection() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.APPLIED);
		NewTimelineEvent interviewWithReason = NewTimelineEvent.builder()
			.eventDate(TODAY)
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.rejectionReason(RejectionReason.OTHER)
			.build();

		assertThatThrownBy(() -> this.applications.addEvent(this.userId, id, interviewWithReason))
			.isInstanceOfSatisfying(BusinessRuleException.class, exception -> assertThat(exception.getErrorCode())
				.isEqualTo(JobsErrorCode.REJECTION_REASON_NOT_ALLOWED));

		this.applications.addEvent(this.userId, id, NewTimelineEvent.builder()
			.eventDate(TODAY)
			.stageCategory(ApplicationStatus.REJECTED)
			.stageLabel("After the final round")
			.note("They hired internally")
			.rejectionReason(RejectionReason.POSITION_FILLED)
			.build());

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.REJECTED);
		assertThat(application.timeline()).hasSize(2).last().satisfies(event -> {
			assertThat(event.stageLabel()).isEqualTo("After the final round");
			assertThat(event.note()).isEqualTo("They hired internally");
			assertThat(event.rejectionReason()).isEqualTo(RejectionReason.POSITION_FILLED);
		});
	}

	@Test
	void deletesTheApplicationWithItsTimeline() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.APPLIED);
		this.applications.addEvent(this.userId, id, event(TODAY, ApplicationStatus.INTERVIEWING));

		this.applications.delete(this.userId, id);

		assertThat(this.applications.findById(this.userId, id)).isEmpty();
		assertThat(this.jdbc.sql("SELECT count(*) FROM application_events").query(Long.class).single()).isZero();
	}

	@Test
	void tracksALinkOncePerProfile() {
		TrackedApplication first = this.applications.track(newApplication(this.userId, "Backend engineer",
				ApplicationStatus.SAVED));
		TrackedApplication again = this.applications.track(newApplication(this.userId, "Backend engineer",
				ApplicationStatus.SAVED));
		TrackedApplication othersCopy = this.applications.track(newApplication(this.otherUserId, "Backend engineer",
				ApplicationStatus.SAVED));

		assertThat(first.created()).isTrue();
		assertThat(again).isEqualTo(new TrackedApplication(first.id(), false));
		assertThat(othersCopy.created()).isTrue();
		assertThat(this.applications.findAll(this.userId, null)).hasSize(1);
	}

	@Test
	void savingABookmarkedPostingMarksItApplied() {
		long id = this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.SAVED))
			.id();

		this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.APPLIED));

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory)
			.containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED);
	}

	@Test
	void neverMergesApplicationsWithoutALink() {
		this.applications.track(newApplication(this.userId, "Referral", ApplicationStatus.APPLIED, null));
		this.applications.track(newApplication(this.userId, "Another referral", ApplicationStatus.APPLIED, null));

		assertThat(this.applications.findAll(this.userId, null)).hasSize(2);
	}

	@Test
	void capturingAgainNeverMovesAnApplicationBack() {
		long id = this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.APPLIED))
			.id();
		this.applications.addEvent(this.userId, id, event(TODAY, ApplicationStatus.INTERVIEWING));

		this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.SAVED));
		this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.APPLIED));

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.INTERVIEWING);
		assertThat(application.timeline()).hasSize(2);
	}

	@Test
	void findsTheApplicationTrackingALink() {
		long id = this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.SAVED))
			.id();

		assertThat(this.applications.findByLink(this.userId, "https://example.com/jobs/1"))
			.hasValueSatisfying(application -> assertThat(application.id()).isEqualTo(id));
		assertThat(this.applications.findByLink(this.userId, "https://example.com/jobs/2")).isEmpty();
		assertThat(this.applications.findByLink(this.otherUserId, "https://example.com/jobs/1")).isEmpty();
	}

	@Test
	void movesABookmarkAppliedToByMistakeBackToSaved() {
		long id = this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.SAVED))
			.id();
		this.applications.track(newApplication(this.userId, "Backend engineer", ApplicationStatus.APPLIED));

		this.applications.moveBackToSaved(this.userId, id);

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.SAVED);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory)
			.containsExactly(ApplicationStatus.SAVED);
	}

	@Test
	void movesAnApplicationSavedAsAppliedBackToSavedAtTheSameMoment() {
		long id = this.applications.create(NewApplication.builder()
			.userId(this.userId)
			.title("Backend engineer")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(TODAY.minusDays(2))
			.initialStatusTime(LocalTime.of(10, 30))
			.build());

		this.applications.moveBackToSaved(this.userId, id);

		ApplicationDetails application = this.applications.findById(this.userId, id).orElseThrow();
		assertThat(application.status()).isEqualTo(ApplicationStatus.SAVED);
		assertThat(application.timeline()).extracting(TimelineEvent::stageCategory, TimelineEvent::eventDate,
				TimelineEvent::eventTime)
			.containsExactly(tuple(ApplicationStatus.SAVED, TODAY.minusDays(2), LocalTime.of(10, 30)));
	}

	@Test
	void movesBackToSavedOnlyAnApplicationThatGotNoFurtherThanApplied() {
		long bookmarked = create(this.userId, "Bookmarked", ApplicationStatus.SAVED);
		long interviewing = create(this.userId, "Interviewing", ApplicationStatus.APPLIED);
		this.applications.addEvent(this.userId, interviewing, event(TODAY, ApplicationStatus.INTERVIEWING));
		// Back at APPLIED, but the interview stays on the timeline
		this.applications.addEvent(this.userId, interviewing, event(TODAY.plusDays(1), ApplicationStatus.APPLIED));

		for (long id : new long[] { bookmarked, interviewing }) {
			assertThatThrownBy(() -> this.applications.moveBackToSaved(this.userId, id))
				.isInstanceOfSatisfying(BusinessRuleException.class, exception -> assertThat(exception.getErrorCode())
					.isEqualTo(JobsErrorCode.CANNOT_MOVE_BACK_TO_SAVED));
		}
		assertThat(this.applications.findById(this.userId, interviewing).orElseThrow().timeline()).hasSize(3);
	}

	@Test
	void movesBackOnlyTheProfilesOwnApplications() {
		long id = create(this.userId, "Backend engineer", ApplicationStatus.APPLIED);

		assertThatExceptionOfType(ApplicationNotFoundException.class)
			.isThrownBy(() -> this.applications.moveBackToSaved(this.otherUserId, id));
	}

	private long insertProfile(String name) {
		return this.jdbc.sql("INSERT INTO users (name) VALUES (?) RETURNING id")
			.param(name)
			.query(Long.class)
			.single();
	}

	private long create(long userId, String title, ApplicationStatus initialStatus) {
		return this.applications.create(newApplication(userId, title, initialStatus));
	}

	private static NewApplication newApplication(long userId, String title, ApplicationStatus initialStatus) {
		return newApplication(userId, title, initialStatus, "https://example.com/jobs/1");
	}

	private static NewApplication newApplication(long userId, String title, ApplicationStatus initialStatus,
			@Nullable String link) {
		return NewApplication.builder()
			.userId(userId)
			.title(title)
			.company("Acme")
			.description("Build things")
			.link(link)
			.location("Remote")
			.employmentType("Full-time")
			.rawPayload("{\"@type\": \"JobPosting\"}")
			.initialStatus(initialStatus)
			.initialStatusDate(TODAY)
			.build();
	}

	private static NewTimelineEvent event(LocalDate date, ApplicationStatus stageCategory) {
		return event(date, null, stageCategory);
	}

	private static NewTimelineEvent event(LocalDate date, @Nullable LocalTime time, ApplicationStatus stageCategory) {
		return NewTimelineEvent.builder().eventDate(date).eventTime(time).stageCategory(stageCategory).build();
	}

}
