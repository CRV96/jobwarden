package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.mcp.dto.PostingCheck;
import com.jobwarden.mcp.enums.Verdict;
import com.jobwarden.search.SearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * {@code @WebMvcTest} loads only the controller: the tool sets it calls are imported, with the services they use mocked.
 */
@WebMvcTest(McpController.class)
@Import({ JobTools.class, DiscoverTools.class, Profiles.class })
class McpControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final User WORK = new User(2, "Work", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private PostingChecker checker;

	@BeforeEach
	void oneProfile() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));
		given(this.users.exists(1)).willReturn(true);
	}

	@Test
	void introducesItself() {
		assertThat(rpc("initialize", """
				{"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "claude-code", "version": "2"}}"""))
			.hasStatusOk()
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.protocolVersion").isEqualTo("2025-03-26");
				assertThat(body).extractingPath("$.result.serverInfo.name").isEqualTo("jobwarden");
				assertThat(body).extractingPath("$.result.capabilities.tools.listChanged").isEqualTo(false);
				assertThat(body).extractingPath("$.result.instructions").asString().contains("check_postings");
			});
	}

	@Test
	void answersAnUnknownProtocolVersionWithItsNewest() {
		assertThat(rpc("initialize", "{\"protocolVersion\": \"2023-01-01\"}")).bodyJson()
			.extractingPath("$.result.protocolVersion")
			.isEqualTo(McpController.PROTOCOL_VERSIONS.getFirst());
	}

	@Test
	void acceptsNotificationsWithoutAnswering() {
		assertThat(post("{\"jsonrpc\": \"2.0\", \"method\": \"notifications/initialized\"}"))
			.hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void listsItsTools() {
		assertThat(rpc("tools/list", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.tools[*].name").asArray()
				.contains("list_profiles", "list_jobs", "get_job", "check_postings", "update_job_status",
						"list_saved_searches", "save_search", "add_found_jobs", "list_found_jobs");
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'update_job_status')].inputSchema.required[*]")
				.asArray()
				.containsExactly("job_id", "status");
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'list_jobs')].annotations.readOnlyHint")
				.asArray()
				.containsExactly(true);
		});
	}

	@Test
	void listsTheJobsInSnakeCase() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(ApplicationSummary.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.link("https://jobs.lever.co/acme/7")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.build())));

		assertThat(callTool("list_jobs", "{\"status\": \"applied\"}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.isError").isEqualTo(false);
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].link").isEqualTo("https://jobs.lever.co/acme/7");
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].last_modified").isEqualTo("2026-10-20T10:00:00Z");
			// Fields without a value are left out
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0]").asMap().doesNotContainKey("salary");
			assertThat(body).extractingPath("$.result.content[0].type").isEqualTo("text");
		});
	}

	@Test
	void needsAProfileWhenThereAreSeveral() {
		given(this.users.findAll()).willReturn(List.of(ROBERT, WORK));

		assertThat(callTool("list_jobs", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
			assertThat(body).extractingPath("$.result.content[0].text").asString().contains("pass profile_id");
		});
	}

	@Test
	void takesTheProfileFromTheHeader() {
		given(this.users.findAll()).willReturn(List.of(ROBERT, WORK));
		given(this.users.exists(2)).willReturn(true);
		given(this.applications.find(eq(2L), any())).willReturn(new PageImpl<>(List.of()));

		assertThat(this.mvc.post()
			.uri("/mcp")
			.header(McpController.PROFILE_HEADER, "2")
			.contentType(MediaType.APPLICATION_JSON)
			.content(request("tools/call", "{\"name\": \"list_jobs\", \"arguments\": {}}")))
			.bodyJson()
			.extractingPath("$.result.structuredContent.total")
			.isEqualTo(0);
	}

	@Test
	void movesAJobToAnotherStatus() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.APPLIED)),
				Optional.of(job(ApplicationStatus.POSITION_CLOSED)));

		assertThat(callTool("update_job_status",
				"{\"job_id\": 7, \"status\": \"POSITION_CLOSED\", \"note\": \"The posting is gone\"}"))
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.structuredContent.previous_status").isEqualTo("APPLIED");
				assertThat(body).extractingPath("$.result.structuredContent.status").isEqualTo("POSITION_CLOSED");
			});
		ArgumentCaptor<NewTimelineEvent> event = ArgumentCaptor.forClass(NewTimelineEvent.class);
		then(this.applications).should().addEvent(eq(1L), eq(7L), event.capture());
		assertThat(event.getValue().stageCategory()).isEqualTo(ApplicationStatus.POSITION_CLOSED);
		assertThat(event.getValue().note()).isEqualTo("The posting is gone");
		assertThat(event.getValue().eventDate()).isEqualTo(LocalDate.now());
	}

	@Test
	void saysWhatIsMissing() {
		assertThat(callTool("update_job_status", "{\"status\": \"REJECTED\"}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
			assertThat(body).extractingPath("$.result.content[0].text").isEqualTo("job_id is required");
		});
		assertThat(callTool("update_job_status", "{\"job_id\": 7, \"status\": \"HIRED\"}")).bodyJson()
			.extractingPath("$.result.content[0].text")
			.asString()
			.startsWith("status must be one of");
	}

	@Test
	void closesTheJobsWhosePostingIsCertainlyGone() {
		given(this.applications.findAll(1, null)).willReturn(List.of(summary(7, ApplicationStatus.APPLIED, "https://a.example/7"),
				summary(8, ApplicationStatus.INTERVIEWING, "https://a.example/8"),
				summary(9, ApplicationStatus.REJECTED, "https://a.example/9")));
		given(this.checker.check(any())).willAnswer(call -> new PostingCheck(call.getArgument(0), Verdict.CLOSED, true,
				"The page is gone (404)", 404, null, null));

		assertThat(callTool("check_postings", "{\"close_when_gone\": true}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.summary.CLOSED").isEqualTo(2);
			assertThat(body).extractingPath("$.result.structuredContent.checked[*].job_id").asArray().containsExactly(7, 8);
			assertThat(body).extractingPath("$.result.structuredContent.checked[*].closed").asArray()
				.containsExactly(true, false);
		});
		// The interview may have filled the position itself: only the Applied job is closed
		then(this.applications).should().addEvent(eq(1L), eq(7L), any());
		then(this.applications).should(never()).addEvent(eq(1L), eq(8L), any());
	}

	@Test
	void checksAnAddress() {
		given(this.checker.check("https://jobs.lever.co/acme/1")).willReturn(new PostingCheck("https://jobs.lever.co/acme/1",
				Verdict.OPEN, true, "The page has the posting's job data, still valid", 200, null, null));

		assertThat(callTool("check_postings", "{\"url\": \"https://jobs.lever.co/acme/1\"}")).bodyJson()
			.extractingPath("$.result.structuredContent.verdict")
			.isEqualTo("OPEN");
		then(this.applications).should(never()).addEvent(anyLong(), anyLong(), any());
	}

	@Test
	void givesTheSavedSearchesWithQueriesToRun() {
		given(this.discovery.findSearches(1)).willReturn(List.of(new SavedSearch(3, "Remote Java",
				SearchCriteria.builder().titles(List.of("Java Developer")).sites(List.of("jobs.lever.co")).build(),
				Instant.now())));

		assertThat(callTool("list_saved_searches", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.searches[0].queries.GOOGLE.query")
				.isEqualTo("site:jobs.lever.co \"Java Developer\"");
			assertThat(body).extractingPath("$.result.structuredContent.searches[0].queries.LINKEDIN.url").asString()
				.startsWith("https://www.linkedin.com/jobs/search/?keywords=");
			assertThat(body).extractingPath("$.result.structuredContent.searches[0].criteria.titles[0]")
				.isEqualTo("Java Developer");
		});
	}

	@Test
	void addsTheJobsItFound() {
		given(this.discovery.addFoundJobs(eq(1L), any())).willAnswer(call -> new AddedFoundJobs(List.of(FoundJob.builder()
			.id(11)
			.title("Java Developer")
			.link("https://jobs.lever.co/acme/1")
			.decision(Decision.PENDING)
			.foundAt(Instant.now())
			.build()), List.of(new AddedFoundJobs.SkippedLink("https://jobs.lever.co/acme/2", "already found"))));
		given(this.discovery.countToReview(1, null)).willReturn(4L);

		assertThat(callTool("add_found_jobs", """
				{"search_id": 3, "jobs": [
				  {"title": "Java Developer", "link": "https://jobs.lever.co/acme/1", "note": "Remote, Kafka", "posted_on": "2026-10-24"},
				  {"title": "Java Developer", "link": "https://jobs.lever.co/acme/2"}]}"""))
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.structuredContent.added[0].id").isEqualTo(11);
				assertThat(body).extractingPath("$.result.structuredContent.skipped[0].reason").isEqualTo("already found");
				assertThat(body).extractingPath("$.result.structuredContent.waiting_for_a_decision").isEqualTo(4);
			});
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<NewFoundJob>> jobs = ArgumentCaptor.forClass(List.class);
		then(this.discovery).should().addFoundJobs(eq(1L), jobs.capture());
		assertThat(jobs.getValue().getFirst().searchId()).isEqualTo(3);
		assertThat(jobs.getValue().getFirst().postedOn()).isEqualTo(LocalDate.of(2026, 10, 24));
	}

	@Test
	void refusesPagesFromOtherSites() {
		assertThat(this.mvc.post()
			.uri("/mcp")
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.contentType(MediaType.APPLICATION_JSON)
			.content(request("tools/list", "{}"))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.post()
			.uri("/mcp")
			.header(HttpHeaders.ORIGIN, "http://localhost:6274")
			.contentType(MediaType.APPLICATION_JSON)
			.content(request("tools/list", "{}"))).hasStatusOk();
	}

	@Test
	void answersProtocolErrors() {
		assertThat(post("{ not json")).bodyJson().extractingPath("$.error.code").isEqualTo(-32700);
		assertThat(rpc("resources/list", "{}")).bodyJson().extractingPath("$.error.code").isEqualTo(-32601);
		assertThat(rpc("tools/call", "{\"name\": \"delete_everything\"}")).bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo(-32602);
	}

	@Test
	void neverStreams() {
		assertThat(this.mvc.get().uri("/mcp")).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
	}

	private MvcTestResult callTool(String name, String arguments) {
		return rpc("tools/call", "{\"name\": \"" + name + "\", \"arguments\": " + arguments + "}");
	}

	private MvcTestResult rpc(String method, String params) {
		return post(request(method, params));
	}

	private MvcTestResult post(String body) {
		return this.mvc.post().uri("/mcp").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String request(String method, String params) {
		return "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + method + "\", \"params\": " + params + "}";
	}

	private static ApplicationDetails job(ApplicationStatus status) {
		return ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.status(status)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build();
	}

	private static ApplicationSummary summary(long id, ApplicationStatus status, String link) {
		return ApplicationSummary.builder()
			.id(id)
			.title("Job " + id)
			.link(link)
			.status(status)
			.capturedAt(Instant.now())
			.build();
	}

}
