package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.discover.dto.AddedFoundJobs;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.NewFoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.AddedHiringPosts;
import com.jobwarden.hiring.dto.AddedRecruiters;
import com.jobwarden.hiring.dto.NewHiringPost;
import com.jobwarden.hiring.dto.NewRecruiter;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
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
@Import({ JobTools.class, DiscoverTools.class, CvTools.class, RequestTools.class, HiringTools.class, ApplyTools.class,
		Profiles.class })
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

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private HiringService hiring;

	@MockitoBean
	private AutoApplyService autoApply;

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
						"update_follow_up", "get_cv", "save_tailored_cv", "list_requests", "get_request", "finish_request", "list_saved_searches", "save_search", "add_found_jobs", "list_found_jobs", "list_recruiters", "add_recruiters", "list_hiring_posts", "add_hiring_posts",
						"get_application_answers", "list_apply_queue", "start_apply", "report_apply_result");
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
	void logsAnInterviewWithItsTimeAndPlace() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.APPLIED)),
				Optional.of(job(ApplicationStatus.INTERVIEWING)));

		assertThat(callTool("update_job_status", """
				{"job_id": 7, "status": "INTERVIEWING", "date": "2026-11-03", "time": "14:30", "stage": "HR call",
				 "interview_format": "video", "interview_location": "https://meet.example.com/x", "duration_minutes": 30}
				""")).bodyJson().extractingPath("$.result.isError").isEqualTo(false);
		ArgumentCaptor<NewTimelineEvent> event = ArgumentCaptor.forClass(NewTimelineEvent.class);
		then(this.applications).should().addEvent(eq(1L), eq(7L), event.capture());
		assertThat(event.getValue()).isEqualTo(NewTimelineEvent.builder()
			.eventDate(LocalDate.of(2026, 11, 3))
			.eventTime(LocalTime.of(14, 30))
			.stageCategory(ApplicationStatus.INTERVIEWING)
			.stageLabel("HR call")
			.interviewFormat(InterviewFormat.VIDEO)
			.interviewLocation("https://meet.example.com/x")
			.durationMinutes(30)
			.build());
	}

	@Test
	void changesOnlyTheFollowUpFieldsItIsGiven() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.now())
			.followUp(FollowUp.builder().contactName("Ana").contactPhone("+40 721 000 000").build())
			.timeline(List.of())
			.build()));

		assertThat(callTool("update_follow_up",
				"{\"job_id\": 7, \"contact_email\": \"ana@acme.example\", \"contact_phone\": \"\", \"follow_up_on\": \"2026-11-10\"}"))
			.bodyJson()
			.extractingPath("$.result.structuredContent.follow_up.contact_email")
			.isEqualTo("ana@acme.example");
		then(this.applications).should().updateFollowUp(1, 7, FollowUp.builder()
			.contactName("Ana")
			.contactEmail("ana@acme.example")
			.followUpOn(LocalDate.of(2026, 11, 10))
			.build());
	}

	@Test
	void givesTheCvWithoutItsContactDetails() {
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("cv.pdf", "application/pdf", 84_000, 12, Instant.parse("2026-10-20T10:00:00Z")),
				CvDetails.builder()
					.fullName("Robert")
					.email("robert@example.com")
					.headline("Senior Java Developer")
					.skills(List.of("Java", "Kafka"))
					.experience(List.of(new CvExperience("Java Developer", "Acme", "Bucharest", "2021", "Present", null)))
					.build()));
		given(this.cvs.fileText(1)).willReturn(Optional.of("Java, Spring"));

		assertThat(callTool("get_cv", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.has_cv").isEqualTo(true);
			assertThat(body).extractingPath("$.result.structuredContent.fields.headline").isEqualTo("Senior Java Developer");
			assertThat(body).extractingPath("$.result.structuredContent.fields.experience[0].company").isEqualTo("Acme");
			assertThat(body).extractingPath("$.result.structuredContent.fields").asMap().doesNotContainKey("email");
			assertThat(body).extractingPath("$.result.structuredContent.file.text").isEqualTo("Java, Spring");
		});
	}

	@Test
	void saysWhenThereIsNoCv() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));

		assertThat(callTool("get_cv", "{}")).bodyJson()
			.extractingPath("$.result.structuredContent")
			.asMap()
			.containsEntry("has_cv", false)
			.doesNotContainKeys("fields", "file");
	}

	@Test
	void handsOutTheWaitingRequestsWithTheirInstructions() {
		AiRequest waiting = AiRequest.builder()
			.id(12)
			.kind(AiRequestKind.FIND_JOBS_FOR_CV)
			.requirements(AiRequirements.builder().locations(List.of("Romania")).maxJobs(10).build())
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.findOpenRequests(1)).willReturn(List.of(waiting));
		given(this.discovery.startRequest(1, 12)).willReturn(waiting);

		assertThat(callTool("list_requests", "{}")).bodyJson()
			.extractingPath("$.result.structuredContent.requests[0].kind")
			.isEqualTo("FIND_JOBS_FOR_CV");
		assertThat(callTool("get_request", "{\"request_id\": 12}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.requirements.locations[0]").isEqualTo("Romania");
			assertThat(body).extractingPath("$.result.structuredContent.requirements.use_cv").isEqualTo(true);
			assertThat(body).extractingPath("$.result.structuredContent.instructions").asString()
				.contains("Call get_cv", "add_found_jobs and request_id 12, at most 10 jobs", "finish_request with request_id 12");
		});
		then(this.discovery).should().startRequest(1, 12);
	}

	@Test
	void handsOutARequestToTailorTheCvToAJob() {
		AiRequest waiting = AiRequest.builder()
			.id(14)
			.kind(AiRequestKind.TAILOR_CV)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.applicationId(7L)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.startRequest(1, 14)).willReturn(waiting);

		assertThat(callTool("get_request", "{\"request_id\": 14}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.job_id").isEqualTo(7);
			assertThat(body).doesNotHavePath("$.result.structuredContent.requirements");
			assertThat(body).extractingPath("$.result.structuredContent.instructions").asString()
				.contains("Call get_cv", "get_job with job_id 7", "never add a skill", "save_tailored_cv and job_id 7",
						"finish_request with request_id 14");
		});
	}

	@Test
	void savesTheCvTailoredToAJobWithTheCvPagesContactDetails() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company("Acme")
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.fullName("Robert Ciapă")
			.email("robert@example.com")
			.github("github.com/robert")
			.build()));
		given(this.cvs.saveTailored(eq(1L), eq(7L), any(), any(), any(), any())).willAnswer(call -> TailoredCv.builder()
			.applicationId(7)
			.details(call.getArgument(2))
			.matchedKeywords(call.getArgument(3))
			.missingKeywords(call.getArgument(4))
			.updatedAt(Instant.now())
			.build());

		assertThat(callTool("save_tailored_cv", """
				{"job_id": 7, "cv": {"full_name": "Someone Else", "email": "other@example.com", "phone": "+40 700 000 000",
				 "headline": "Backend Engineer", "skills": ["Spring Boot", "Kafka"],
				 "experience": [{"role": "Java Developer", "company": "Acme", "start": "2021", "end": "Present",
				                 "description": "Built payments"}],
				 "languages": [{"language": "English", "level": "C1"}]},
				 "matched_keywords": ["Spring Boot"], "missing_keywords": ["Kubernetes"], "notes": "Put Kafka first."}
				""")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.title").isEqualTo("Backend engineer");
			assertThat(body).extractingPath("$.result.structuredContent.jobs").isEqualTo(1);
			assertThat(body).extractingPath("$.result.structuredContent.page").isEqualTo("/applications/7");
		});
		ArgumentCaptor<CvDetails> saved = ArgumentCaptor.forClass(CvDetails.class);
		then(this.cvs).should().saveTailored(eq(1L), eq(7L), saved.capture(), eq(List.of("Spring Boot")),
				eq(List.of("Kubernetes")), eq("Put Kafka first."));
		assertThat(saved.getValue()).satisfies(cv -> {
			assertThat(cv.fullName()).isEqualTo("Robert Ciapă");
			assertThat(cv.email()).isEqualTo("robert@example.com");
			assertThat(cv.phone()).isEqualTo("+40 700 000 000");
			assertThat(cv.github()).isEqualTo("github.com/robert");
			assertThat(cv.headline()).isEqualTo("Backend Engineer");
			assertThat(cv.experience()).containsExactly(
					new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Built payments"));
			assertThat(cv.languages()).containsExactly(new CvLanguage("English", "C1"));
		});
	}

	@Test
	void savesATailoredCvOnlyForTheProfilesOwnJobs() {
		given(this.applications.findById(1, 8)).willReturn(Optional.empty());

		assertThat(callTool("save_tailored_cv", "{\"job_id\": 8, \"cv\": {\"headline\": \"Backend Engineer\"}}"))
			.bodyJson()
			.extractingPath("$.result.isError")
			.isEqualTo(true);
		then(this.cvs).should(never()).saveTailored(anyLong(), anyLong(), any(), any(), any(), any());
	}

	@Test
	void addsHiringPostsWithTheirAuthors() {
		given(this.hiring.addPosts(eq(1L), any())).willReturn(new AddedHiringPosts(List.of(), List.of("https://example.com/2")));

		assertThat(callTool("add_hiring_posts", """
				{"posts": [{"link": "https://www.linkedin.com/posts/ana-1", "text": "We're hiring #java",
				            "posted_on": "2026-10-20", "role": "Java Developer", "company": "Acme", "country": "RO",
				            "author": {"name": "Ana Pop", "headline": "IT Recruiter", "profile_url": "https://www.linkedin.com/in/ana"}}]}
				""")).bodyJson()
			.extractingPath("$.result.structuredContent.skipped[0]")
			.isEqualTo("https://example.com/2");
		then(this.hiring).should().addPosts(1, List.of(NewHiringPost.builder()
			.link("https://www.linkedin.com/posts/ana-1")
			.text("We're hiring #java")
			.postedOn(LocalDate.of(2026, 10, 20))
			.role("Java Developer")
			.company("Acme")
			.country("RO")
			.authorName("Ana Pop")
			.authorHeadline("IT Recruiter")
			.authorProfileUrl("https://www.linkedin.com/in/ana")
			.build()));
		assertThat(callTool("add_hiring_posts", "{\"posts\": [{\"link\": \"https://example.com/1\", \"text\": \"Hiring\"}]}"))
			.bodyJson()
			.extractingPath("$.result.content[0].text")
			.isEqualTo("author is required, as an object");
	}

	@Test
	void addsTheRecruitersFoundWithWhyToFollowThem() {
		given(this.hiring.addFoundRecruiters(eq(1L), any())).willReturn(new AddedRecruiters(List.of(Recruiter.builder()
			.id(4)
			.name("Bob Ionescu")
			.profileUrl("https://www.linkedin.com/in/bob")
			.company("Acme")
			.origin(RecruiterOrigin.FOUND)
			.build()), List.of()));

		assertThat(callTool("add_recruiters", """
				{"recruiters": [{"name": "Bob Ionescu", "headline": "Technical Recruiter at Acme",
				                 "profile_url": "https://www.linkedin.com/in/bob", "company": "Acme", "country": "RO",
				                 "note": "Hires Java developers for the payments team."}]}
				""")).bodyJson()
			.extractingPath("$.result.structuredContent.added[0].origin")
			.isEqualTo("FOUND");
		then(this.hiring).should().addFoundRecruiters(1, List.of(NewRecruiter.builder()
			.name("Bob Ionescu")
			.headline("Technical Recruiter at Acme")
			.profileUrl("https://www.linkedin.com/in/bob")
			.company("Acme")
			.country("RO")
			.note("Hires Java developers for the payments team.")
			.build()));
	}

	@Test
	void handsOutARequestForRecruiters() {
		AiRequest waiting = AiRequest.builder()
			.id(22)
			.kind(AiRequestKind.FIND_RECRUITERS)
			.requirements(AiRequirements.builder().titles(List.of("Java Developer")).locations(List.of("Romania")).maxJobs(20).build())
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build();
		given(this.discovery.startRequest(1, 22)).willReturn(waiting);

		assertThat(callTool("get_request", "{\"request_id\": 22}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.requirements.titles[0]").isEqualTo("Java Developer");
			assertThat(body).extractingPath("$.result.structuredContent.instructions").asString()
				.contains("list_recruiters", "list_jobs with status SAVED", "add_recruiters, at most 20",
						"finish_request with request_id 22");
		});
	}

	@Test
	void listsTheRecruitersTheProfileFollowsFirst() {
		given(this.hiring.findRecruiters(1)).willReturn(List.of(Recruiter.builder()
			.id(3)
			.name("Ana Pop")
			.profileUrl("https://www.linkedin.com/in/ana")
			.followed(true)
			.posts(4)
			.build()));

		assertThat(callTool("list_recruiters", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.recruiters[0].profile_url")
				.isEqualTo("https://www.linkedin.com/in/ana");
			assertThat(body).extractingPath("$.result.structuredContent.recruiters[0].followed").isEqualTo(true);
		});
	}

	@Test
	void givesTheAnswersToFillFormsInWith() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.fullName("Robert Ciapă")
			.email("robert@example.com")
			.phone("+40 700 000 000")
			.build()));
		given(this.autoApply.findAnswers(1)).willReturn(ApplicationAnswers.builder()
			.workAuthorization("EU citizen")
			.needsSponsorship(false)
			.noticePeriod("1 month")
			.coverLetter(CoverLetter.WRITE)
			.declineVoluntary(true)
			.custom(List.of(new CustomAnswer("Driving licence?", "Yes, B")))
			.build());

		assertThat(callTool("get_application_answers", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.contact.email").isEqualTo("robert@example.com");
			assertThat(body).extractingPath("$.result.structuredContent.work_authorization").isEqualTo("EU citizen");
			assertThat(body).extractingPath("$.result.structuredContent.needs_sponsorship").isEqualTo(false);
			assertThat(body).extractingPath("$.result.structuredContent.cover_letter").isEqualTo("WRITE");
			assertThat(body).extractingPath("$.result.structuredContent.custom[0].answer").isEqualTo("Yes, B");
		});
	}

	@Test
	void listsTheJobsToApplyToWithTheCvToUpload() {
		given(this.autoApply.findOpenItems(1)).willReturn(List.of(ApplyItem.builder()
			.id(3)
			.applicationId(7)
			.title("Backend engineer")
			.company("Acme")
			.link("https://jobs.lever.co/acme/7")
			.mode(ApplyMode.SUBMIT)
			.status(ApplyStatus.QUEUED)
			.createdAt(Instant.now())
			.build()));

		assertThat(callTool("list_apply_queue", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].item_id").isEqualTo(3);
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].mode").isEqualTo("SUBMIT");
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].has_tailored_cv").isEqualTo(false);
			assertThat(body).extractingPath("$.result.structuredContent.jobs[0].cv_url")
				.isEqualTo("http://localhost/claude/cv?profile=1&job=7");
		});
	}

	@Test
	void reportsHowApplyingWent() {
		given(this.autoApply.report(1, 3, ApplyStatus.NEEDS_ANSWER, "Workday", null, "Willing to work weekends?"))
			.willReturn(ApplyItem.builder()
				.id(3)
				.applicationId(7)
				.title("Backend engineer")
				.mode(ApplyMode.SUBMIT)
				.status(ApplyStatus.NEEDS_ANSWER)
				.question("Willing to work weekends?")
				.createdAt(Instant.now())
				.build());

		assertThat(callTool("report_apply_result", """
				{"item_id": 3, "result": "NEEDS_ANSWER", "site": "Workday", "question": "Willing to work weekends?"}
				""")).bodyJson()
			.extractingPath("$.result.structuredContent.status")
			.isEqualTo("NEEDS_ANSWER");
		then(this.autoApply).should().report(1, 3, ApplyStatus.NEEDS_ANSWER, "Workday", null, "Willing to work weekends?");
	}

	@Test
	void handsOutARequestToApplyWithItsRules() {
		given(this.discovery.startRequest(1, 30)).willReturn(AiRequest.builder()
			.id(30)
			.kind(AiRequestKind.AUTO_APPLY)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 30}")).bodyJson()
			.extractingPath("$.result.structuredContent.instructions")
			.asString()
			.contains("Claude in Chrome", "get_application_answers", "list_apply_queue", "start_apply", "LinkedIn Easy Apply",
					"Never solve or get around a CAPTCHA", "NEEDS_ANSWER", "Mode REVIEW", "finish_request with request_id 30");
	}

	@Test
	void finishesARequestWithASummary() {
		given(this.discovery.finishRequest(1, 12, false, "LinkedIn blocked every search")).willReturn(AiRequest.builder()
			.id(12)
			.kind(AiRequestKind.FIND_JOBS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.FAILED)
			.summary("LinkedIn blocked every search")
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("finish_request", "{\"request_id\": 12, \"summary\": \"LinkedIn blocked every search\", \"done\": false}"))
			.bodyJson()
			.extractingPath("$.result.structuredContent.status")
			.isEqualTo("FAILED");
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
				{"search_id": 3, "request_id": 12, "jobs": [
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
		assertThat(jobs.getValue().getFirst().requestId()).isEqualTo(12);
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
