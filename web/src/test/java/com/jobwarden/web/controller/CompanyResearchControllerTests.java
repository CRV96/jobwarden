package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.CompanyResearch;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CompanyResearchService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(CompanyResearchController.class)
class CompanyResearchControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CompanyResearchService research;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobertWithAJob() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Senior Java & SAP Commerce Engineer")
			.company("Tires Easy")
			.status(ApplicationStatus.INTERVIEWING)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
	}

	@Test
	void offersToAskTheAssistantToResearchTheCompany() {
		assertThat(this.mvc.get().uri("/applications/7/research").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("id=\"company-research\"", "Company research", "reviews", "/applications/7/research/request",
					"Ask the AI Assistant to research it")
			.doesNotContain("every 5s");
	}

	@Test
	void handsTheRequestToTheAssistantAndFollowsIt() {
		AiRequest waiting = AiRequest.builder()
			.id(41)
			.kind(AiRequestKind.RESEARCH_COMPANY)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.createRequest(1, AiRequestKind.RESEARCH_COMPANY, 7)).willReturn(waiting);
		given(this.discovery.findLatestRequest(1, AiRequestKind.RESEARCH_COMPANY, 7)).willReturn(Optional.of(waiting));

		assertThat(this.mvc.post().uri("/applications/7/research/request").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Do JobWarden request 41: call get_request with request_id 41", "every 5s",
					"/applications/7/research/cancel");
		then(this.discovery).should().createRequest(1, AiRequestKind.RESEARCH_COMPANY, 7);
	}

	@Test
	void showsWhatTheAssistantFound() {
		given(this.research.find(1, 7)).willReturn(Optional.of(CompanyResearch.builder()
			.company("Tires Easy sells tyres online in the US.")
			.ratings(List.of("Glassdoor 3.9/5 (24 reviews)"))
			.pros(List.of("Friendly team"))
			.cons(List.of("No paid holidays on B2B"))
			.salaries(List.of("Senior Java Developer: 40–45 EUR an hour, B2B"))
			.benefits(List.of("Remote"))
			.news(List.of("2025: moved to SAP Commerce Cloud"))
			.interviews("Two rounds: a recruiter, then a technical call.")
			.watchOut("Few reviews.")
			.sources(List.of("https://www.glassdoor.com/Reviews/Tires-Easy", "javascript:alert(1)"))
			.updatedAt(Instant.now())
			.build()));

		assertThat(this.mvc.get().uri("/applications/7/research").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Tires Easy sells tyres online in the US.", "Glassdoor 3.9/5 (24 reviews)", "Friendly team",
					"No paid holidays on B2B", "Senior Java Developer: 40–45 EUR an hour, B2B", "Remote",
					"2025: moved to SAP Commerce Cloud", "Two rounds: a recruiter, then a technical call.", "Few reviews.",
					"href=\"https://www.glassdoor.com/Reviews/Tires-Easy\"", "Research it again")
			// Only web addresses are links
			.doesNotContain("href=\"javascript:");
	}

	@Test
	void deletesTheResearch() {
		assertThat(this.mvc.post().uri("/applications/7/research/delete").header("HX-Request", "true")).hasStatusOk();
		then(this.research).should().delete(1, 7);
	}

}
