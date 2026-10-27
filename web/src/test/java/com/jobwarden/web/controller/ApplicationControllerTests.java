package com.jobwarden.web.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewApplication;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.RejectionReason;
import com.jobwarden.jobs.exception.JobsErrorCode;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final ApplicationDetails APPLICATION = ApplicationDetails.builder()
		.id(7)
		.title("Backend engineer")
		.company("Acme")
		.description("Build things")
		.link("https://example.com/jobs/7")
		.location("Remote")
		.employmentType("Full-time")
		.source("example.com")
		.status(ApplicationStatus.INTERVIEWING)
		.capturedAt(Instant.parse("2026-09-20T10:00:00Z"))
		.timeline(List.of(
				TimelineEvent.builder()
					.id(1)
					.eventDate(LocalDate.of(2026, 9, 21))
					.stageCategory(ApplicationStatus.APPLIED)
					.build(),
				TimelineEvent.builder()
					.id(2)
					.eventDate(LocalDate.of(2026, 9, 25))
					.eventTime(LocalTime.of(14, 30))
					.stageCategory(ApplicationStatus.INTERVIEWING)
					.stageLabel("Technical interview")
					.note("Went well")
					.build()))
		.build();

	private static final ApplicationSummary SUMMARY = ApplicationSummary.builder()
		.id(7)
		.title("Backend engineer")
		.company("Acme")
		.location("Remote")
		.salary("€5,000 a month")
		.source("example.com")
		.status(ApplicationStatus.INTERVIEWING)
		.capturedAt(Instant.now())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	// For the sidebar, which every page controller fills in
	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(APPLICATION));
	}

	@Test
	void sendsTheBrowserToTheProfilePickerWhenNoProfileIsSelected() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/applications")).hasRedirectedUrl("/profiles");
	}

	@Test
	void tellsHtmxToLoadTheProfilePickerWhenNoProfileIsSelected() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.delete().uri("/applications/7").header("HX-Request", "true")).hasStatusOk()
			.headers()
			.hasValue("HX-Redirect", "/profiles");
	}

	@Test
	void listsTheApplicationsTheAddressAsksFor() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(SUMMARY), PageRequest.of(1, 50), 51));
		given(this.applications.countByStatus(1, "engineer")).willReturn(counts(ApplicationStatus.INTERVIEWING, 51));

		assertThat(this.mvc.get()
			.uri("/applications")
			.param("q", " engineer ")
			.param("status", "INTERVIEWING")
			.param("sort", "TITLE")
			.param("page", "2")
			.param("size", "50")).hasStatusOk()
			.bodyText()
			.contains("Backend engineer", "Acme", "/applications/7", "/applications/7/drawer", "51–51 of 51",
					"/applications?q=engineer&amp;status=INTERVIEWING&amp;sort=TITLE&amp;dir=ASC&amp;size=50",
					"aria-sort=\"ascending\"");
		ApplicationQuery expected = ApplicationQuery.builder()
			.search("engineer")
			.status(ApplicationStatus.INTERVIEWING)
			.sort(ApplicationSort.TITLE)
			.direction(Sort.Direction.ASC)
			.page(1)
			.size(50)
			.build();
		then(this.applications).should().find(1, expected);
	}

	@Test
	void showsTheSitesIconWhereTheProfileHasOne() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(SUMMARY)));
		given(this.applications.countByStatus(1, null)).willReturn(counts(ApplicationStatus.INTERVIEWING, 1));

		assertThat(this.mvc.get().uri("/applications")).bodyText()
			.contains("ph-globe-simple")
			.doesNotContain("/sources/example.com/icon");

		given(this.applications.findSourcesWithIcon(1)).willReturn(Set.of("example.com"));
		assertThat(this.mvc.get().uri("/applications")).bodyText()
			.contains("src=\"/sources/example.com/icon\"")
			.doesNotContain("ph-globe-simple");
	}

	@Test
	void showsTheCompanysLogoOrItsInitials() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(SUMMARY)));
		given(this.applications.countByStatus(1, null)).willReturn(counts(ApplicationStatus.INTERVIEWING, 1));

		assertThat(this.mvc.get().uri("/applications")).bodyText()
			.containsPattern("class=\"company-logo[^\"]*\"[^>]*>\\s*A\\s*</span>")
			.doesNotContain("/applications/7/logo");

		given(this.applications.findApplicationsWithLogo(1)).willReturn(Set.of(7L));
		assertThat(this.mvc.get().uri("/applications")).bodyText().contains("src=\"/applications/7/logo\"");
		assertThat(this.mvc.get().uri("/applications/7")).bodyText().contains("src=\"/applications/7/logo\"");
		assertThat(this.mvc.get().uri("/applications/7/drawer")).bodyText().contains("src=\"/applications/7/logo\"");
	}

	@Test
	void offersTheNewStatusesAndTheAutomatedEmailRejection() {
		assertThat(this.mvc.get().uri("/applications/7")).bodyText()
			.contains("No response", "Position closed", "Automated rejection email", "value=\"AUTOMATED_EMAIL\"");
	}

	@Test
	void listsTheNewestFirstByDefault() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(SUMMARY)));
		given(this.applications.countByStatus(1, null)).willReturn(counts(ApplicationStatus.INTERVIEWING, 1));

		assertThat(this.mvc.get().uri("/applications")).hasStatusOk();

		ArgumentCaptor<ApplicationQuery> query = ArgumentCaptor.captor();
		then(this.applications).should().find(eq(1L), query.capture());
		assertThat(query.getValue()).isEqualTo(ApplicationQuery.builder()
			.sort(ApplicationSort.ADDED)
			.direction(Sort.Direction.DESC)
			.page(0)
			.size(20)
			.build());
	}

	@Test
	void goesToTheLastPageFromAPageThatIsGone() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(4, 20), 41));

		assertThat(this.mvc.get().uri("/applications").param("page", "5").param("status", "APPLIED"))
			.hasRedirectedUrl("/applications?status=APPLIED&page=3");
	}

	@Test
	void saysSoWhenTheSearchFindsNothing() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of()));
		given(this.applications.countByStatus(1, "nothing")).willReturn(counts(ApplicationStatus.APPLIED, 0));

		assertThat(this.mvc.get().uri("/applications").param("q", "nothing")).hasStatusOk()
			.bodyText()
			.contains("No postings match", "Try a different search or status filter.");
	}

	@Test
	void showsTheApplicationWithItsTimeline() {
		assertThat(this.mvc.get().uri("/applications/7")).hasStatusOk()
			.bodyText()
			.contains("Backend engineer", "Technical interview", "Went well", "Lack of experience",
					"25 Sep 2026, 14:30", "21 Sep 2026", "Clear time",
					"hx-post=\"/applications/7/events\"", "id=\"request-error\"")
			.doesNotContain("21 Sep 2026, ");
	}

	@Test
	void answers404ForAnApplicationTheProfileDoesNotOwn() {
		given(this.applications.findById(1, 8)).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/applications/8")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void showsTheFormForAddingAnApplication() {
		assertThat(this.mvc.get().uri("/applications/new")).hasStatusOk()
			.hasViewName("applications/new")
			.bodyText()
			.contains("Add a job", "name=\"statusTime\"", "Clear time");
	}

	@Test
	void addsAnApplicationByHand() {
		given(this.applications.create(any())).willReturn(9L);

		assertThat(this.mvc.post()
			.uri("/applications")
			.param("title", " Platform engineer ")
			.param("company", "Initech")
			.param("link", "")
			.param("status", "APPLIED")
			.param("statusDate", "2026-09-12")
			.param("statusTime", "09:30")).hasRedirectedUrl("/applications/9");
		NewApplication expected = NewApplication.builder()
			.userId(1)
			.title("Platform engineer")
			.company("Initech")
			.initialStatus(ApplicationStatus.APPLIED)
			.initialStatusDate(LocalDate.of(2026, 9, 12))
			.initialStatusTime(LocalTime.of(9, 30))
			.build();
		then(this.applications).should().create(expected);
	}

	@Test
	void showsTheFormAgainWithTheErrorsAndWhatWasTyped() {
		assertThat(this.mvc.post()
			.uri("/applications")
			.param("title", "")
			.param("link", "not a link")
			.param("status", "APPLIED")
			.param("statusDate", "2026-09-12")).hasStatusOk()
			.bodyText()
			.contains("Give the job a title.", "The link must be a full web address", "value=\"not a link\"");
		then(this.applications).should(never()).create(any());
	}

	@Test
	void addsTheEventAndReturnsOnlyTheRefreshedFragment() {
		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("eventTime", "14:05")
			.param("stageCategory", "OFFER")
			.param("stageLabel", " Final call ")
			.param("note", "")).hasStatusOk()
			.satisfies(result -> assertThat(result).headers().hasValue("HX-Trigger", "applications-changed"))
			.bodyText()
			.contains("id=\"application\"")
			.doesNotContain("<html", "id=\"drawer-application\"");
		NewTimelineEvent expected = NewTimelineEvent.builder()
			.eventDate(LocalDate.of(2026, 9, 28))
			.eventTime(LocalTime.of(14, 5))
			.stageCategory(ApplicationStatus.OFFER)
			.stageLabel("Final call")
			.build();
		then(this.applications).should().addEvent(1, 7, expected);
	}

	@Test
	void leavesTheTimeOutWhenItIsCleared() {
		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("eventTime", "")
			.param("stageCategory", "OFFER")).hasStatusOk();

		ArgumentCaptor<NewTimelineEvent> event = ArgumentCaptor.captor();
		then(this.applications).should().addEvent(eq(1L), eq(7L), event.capture());
		assertThat(event.getValue().eventDate()).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(event.getValue().eventTime()).isNull();
	}

	@Test
	void asksWhyForARejectionAddedToTheTimeline() {
		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("stageCategory", "REJECTED")).hasStatusOk()
			.bodyText()
			.contains("Pick why it was rejected.");
		then(this.applications).should(never()).addEvent(anyLong(), anyLong(), any());
	}

	@Test
	void keepsOnlyTheFieldsTheStatusHas() {
		postEvent("NO_RESPONSE", "Second round", "OTHER");
		postEvent("REJECTED", "After the final round", "AUTOMATED_EMAIL");
		postEvent("INTERVIEWING", "Technical interview", "OTHER");

		ArgumentCaptor<NewTimelineEvent> events = ArgumentCaptor.captor();
		then(this.applications).should(times(3)).addEvent(eq(1L), eq(7L), events.capture());
		assertThat(events.getAllValues()).extracting(NewTimelineEvent::stageLabel, NewTimelineEvent::rejectionReason)
			.containsExactly(tuple(null, null), tuple(null, RejectionReason.AUTOMATED_EMAIL),
					tuple("Technical interview", null));
	}

	@Test
	void startsTheTimelineFormWithTheFieldsOfTheCurrentStatus() {
		// APPLICATION is interviewing: the stage shows, the reason doesn't
		assertThat(this.mvc.get().uri("/applications/7")).bodyText()
			.contains("data-status-select", "data-for-statuses=\"INTERVIEWING OFFER\">")
			.containsPattern("data-for-statuses=\"REJECTED\"(?=[^>]*hidden=\"hidden\")(?=[^>]*disabled=\"disabled\")");
	}

	@Test
	void asksWhyForAJobAddedAsRejected() {
		given(this.applications.create(any())).willReturn(9L);

		assertThat(this.mvc.post().uri("/applications").param("title", "Platform engineer").param("status", "REJECTED"))
			.hasStatusOk()
			.bodyText()
			.contains("Pick why it was rejected.");
		assertThat(this.mvc.post()
			.uri("/applications")
			.param("title", "Platform engineer")
			.param("status", "REJECTED")
			.param("rejectionReason", "POSITION_FILLED")).hasRedirectedUrl("/applications/9");
		assertThat(this.mvc.post()
			.uri("/applications")
			.param("title", "Platform engineer")
			.param("status", "APPLIED")
			.param("rejectionReason", "POSITION_FILLED")).hasRedirectedUrl("/applications/9");

		ArgumentCaptor<NewApplication> created = ArgumentCaptor.captor();
		then(this.applications).should(times(2)).create(created.capture());
		assertThat(created.getAllValues()).extracting(NewApplication::initialRejectionReason)
			.containsExactly(RejectionReason.POSITION_FILLED, null);
	}

	@Test
	void statusButtonsRecordTheMomentOfTheClick() {
		LocalTime before = LocalTime.now().withNano(0);

		assertThat(this.mvc.post().uri("/applications/7/events").param("stageCategory", "OFFER")).hasStatusOk();

		ArgumentCaptor<NewTimelineEvent> event = ArgumentCaptor.captor();
		then(this.applications).should().addEvent(eq(1L), eq(7L), event.capture());
		assertThat(event.getValue().eventDate()).isEqualTo(LocalDate.now());
		assertThat(event.getValue().eventTime()).isBetween(before, LocalTime.now());
	}

	@Test
	void showsAnInvalidEventOnTheEventForm() {
		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("stageCategory", "OFFER")
			.param("stageLabel", "x".repeat(201))).hasStatusOk()
			.bodyText()
			.contains("The stage label can be at most 200 characters long.");
		then(this.applications).should(never()).addEvent(anyLong(), anyLong(), any());
	}

	@Test
	void showsABrokenRuleOnTheEventForm() {
		willThrow(new BusinessRuleException(JobsErrorCode.REJECTION_REASON_NOT_ALLOWED, "Only on a rejection"))
			.given(this.applications)
			.addEvent(anyLong(), anyLong(), any());

		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("stageCategory", "OFFER")
			.param("rejectionReason", "OTHER")).hasStatusOk()
			.bodyText()
			.contains("A rejection reason can only be given when the status is Rejected.");
	}

	@Test
	void deletesAndSendsHtmxBackToTheList() {
		assertThat(this.mvc.delete().uri("/applications/7")).hasStatus(HttpStatus.NO_CONTENT)
			.headers()
			.hasValue("HX-Redirect", "/applications");
		then(this.applications).should().delete(1, 7);
	}

	@Test
	void opensTheApplicationInTheSidePanel() {
		assertThat(this.mvc.get().uri("/applications/7/drawer")).hasStatusOk()
			.bodyText()
			.contains("id=\"drawer-application\"", "Backend engineer", "Technical interview", "\"view\": \"DRAWER\"",
					"href=\"/applications/7\"")
			.doesNotContain("<html");
	}

	@Test
	void answersAnEventFromTheSidePanelWithTheSidePanel() {
		assertThat(this.mvc.post().uri("/applications/7/events").param("stageCategory", "OFFER").param("view", "DRAWER"))
			.hasStatusOk()
			.bodyText()
			.contains("id=\"drawer-application\"")
			.doesNotContain("id=\"application\"");
	}

	@Test
	void removesAnEventFromTheTimeline() {
		assertThat(this.mvc.delete().uri("/applications/7/events/2")).hasStatusOk()
			.satisfies(result -> assertThat(result).headers().hasValue("HX-Trigger", "applications-changed"))
			.bodyText()
			.contains("id=\"application\"");
		then(this.applications).should().removeEvent(1, 7, 2);
	}

	@Test
	void showsWhyAnEventWasNotRemoved() {
		willThrow(new BusinessRuleException(JobsErrorCode.CANNOT_REMOVE_LAST_EVENT, "Only event"))
			.given(this.applications)
			.removeEvent(1, 7, 1);

		assertThat(this.mvc.delete().uri("/applications/7/events/1")).hasStatusOk()
			.satisfies(result -> assertThat(result).headers().doesNotContainHeader("HX-Trigger"))
			.bodyText()
			.contains("An application’s timeline keeps at least one event");
	}

	@Test
	void offersToRemoveEventsOnlyWhileThereIsMoreThanOne() {
		assertThat(this.mvc.get().uri("/applications/7")).bodyText()
			.contains("hx-delete=\"/applications/7/events/1\"", "hx-delete=\"/applications/7/events/2\"");

		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(APPLICATION.capturedAt())
			.timeline(List.of(APPLICATION.timeline().getFirst()))
			.build()));
		assertThat(this.mvc.get().uri("/applications/7")).bodyText().doesNotContain("/applications/7/events/1\"");
	}

	@Test
	void savesTheNotes() {
		assertThat(this.mvc.put().uri("/applications/7/notes").param("notes", " Recruiter: Ana ")).hasStatusOk()
			.bodyText()
			.contains("Saved");
		then(this.applications).should().updateNotes(1, 7, "Recruiter: Ana");
	}

	@Test
	void clearsTheNotesWhenTheFieldIsEmptied() {
		assertThat(this.mvc.put().uri("/applications/7/notes").param("notes", "  ")).hasStatusOk();
		then(this.applications).should().updateNotes(1, 7, null);
	}

	@Test
	void correctsASalaryAndReloadsThePage() {
		assertThat(this.mvc.post().uri("/applications/7/salary").param("salary", " 5,000 EUR a month "))
			.hasStatus(HttpStatus.NO_CONTENT)
			.headers()
			.hasValue("HX-Refresh", "true");
		then(this.applications).should().updateSalary(1, 7, "5,000 EUR a month");
	}

	@Test
	void deletesFromTheSidePanelAndReloadsThePageBehindIt() {
		assertThat(this.mvc.delete().uri("/applications/7").param("view", "DRAWER")).hasStatus(HttpStatus.NO_CONTENT)
			.headers()
			.hasValue("HX-Refresh", "true")
			.doesNotContainHeader("HX-Redirect");
		then(this.applications).should().delete(1, 7);
	}

	@Test
	void deletesTheTickedApplicationsAndReloadsTheList() {
		assertThat(this.mvc.delete().uri("/applications").param("ids", "7", "8")).hasStatus(HttpStatus.NO_CONTENT)
			.headers()
			.hasValue("HX-Refresh", "true");
		then(this.applications).should().deleteAll(1, List.of(7L, 8L));
	}

	private static Map<ApplicationStatus, Long> counts(ApplicationStatus status, long count) {
		Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus each : ApplicationStatus.values()) {
			counts.put(each, (each == status) ? count : 0L);
		}
		return counts;
	}

	private void postEvent(String status, String stageLabel, String rejectionReason) {
		assertThat(this.mvc.post()
			.uri("/applications/7/events")
			.param("eventDate", "2026-09-28")
			.param("stageCategory", status)
			.param("stageLabel", stageLabel)
			.param("rejectionReason", rejectionReason)).hasStatusOk();
	}

}
