package com.jobwarden.web.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationQuery;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.enums.ApplicationSort;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(PipelineController.class)
class PipelineControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private JobFitService fits;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@Test
	void putsEachApplicationInTheColumnOfItsStatus() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.find(eq(1L), any())).willReturn(Page.empty());
		given(this.applications.find(eq(1L), argThat(query -> query != null && query.status() == ApplicationStatus.OFFER)))
			.willReturn(new PageImpl<>(List.of(summary(7, "Backend engineer", ApplicationStatus.OFFER))));
		given(this.applications.find(eq(1L), argThat(query -> query != null && query.status() == ApplicationStatus.SAVED)))
			.willReturn(new PageImpl<>(List.of(summary(8, "Platform engineer", ApplicationStatus.SAVED))));

		given(this.fits.findScores(eq(1L), any())).willReturn(Map.of(7L, new BigDecimal("4.4")));

		assertThat(this.mvc.get().uri("/pipeline")).hasStatusOk()
			.hasViewName("pipeline/board")
			.bodyText()
			.contains("Backend engineer", "Platform engineer", "/applications/7/drawer", "st-withdrawn", "Acme · Remote",
					// The job's fit score, in its colour; none for the one not scored
					"tag tabular-nums st-offer", "ph-gauge\"></i>4.4", "draggable=\"true\"")
			// The columns go in the order of the statuses
			.containsSubsequence("st-saved", "Platform engineer", "st-applied", "st-interviewing", "st-offer",
					"Backend engineer", "st-rejected")
			.doesNotContain("/pipeline/column");
	}

	@Test
	void showsTheFirstCardsOfAColumnAndLoadsTheNextOnesAsItsScrolled() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.find(eq(1L), any())).willReturn(Page.empty());
		List<ApplicationSummary> applied = LongStream.rangeClosed(1, PipelineController.PAGE_SIZE)
			.mapToObj(id -> summary(id, "Job " + id, ApplicationStatus.APPLIED))
			.toList();
		given(this.applications.find(eq(1L), argThat(query -> query != null && query.status() == ApplicationStatus.APPLIED)))
			.willReturn(new PageImpl<>(applied, PageRequest.of(0, PipelineController.PAGE_SIZE), 4000));

		assertThat(this.mvc.get().uri("/pipeline")).hasStatusOk()
			.bodyText()
			// The column's count is all of them, its cards only the first page
			.contains(">4000<", "Job 40", "/pipeline/column?status=APPLIED&amp;page=1", "intersect once", "Show 3,960 more")
			.doesNotContain("Job 41");
	}

	@Test
	void givesAColumnsNextCardsForTheSearch() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(
				List.of(summary(81, "Kotlin engineer", ApplicationStatus.APPLIED)), PageRequest.of(2, PipelineController.PAGE_SIZE), 81));

		assertThat(this.mvc.get().uri("/pipeline/column").param("status", "APPLIED").param("page", "2").param("q", " kotlin "))
			.hasStatusOk()
			.bodyText()
			.contains("Kotlin engineer")
			.doesNotContain("/pipeline/column");
		ArgumentCaptor<ApplicationQuery> query = ArgumentCaptor.forClass(ApplicationQuery.class);
		then(this.applications).should().find(eq(1L), query.capture());
		assertThat(query.getValue()).isEqualTo(ApplicationQuery.builder()
			.search("kotlin")
			.status(ApplicationStatus.APPLIED)
			.sort(ApplicationSort.ADDED)
			.direction(Sort.Direction.DESC)
			.page(2)
			.size(PipelineController.PAGE_SIZE)
			.build());
	}

	@Test
	void sendsTheBrowserToTheProfilePickerWhenNoProfileIsSelected() {
		assertThat(this.mvc.get().uri("/pipeline")).hasRedirectedUrl("/profiles");
	}

	private static ApplicationSummary summary(long id, String title, ApplicationStatus status) {
		return ApplicationSummary.builder()
			.id(id)
			.title(title)
			.company("Acme")
			.location("Remote")
			.status(status)
			.capturedAt(Instant.now())
			.build();
	}

}
