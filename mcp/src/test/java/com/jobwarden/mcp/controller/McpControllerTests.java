package com.jobwarden.mcp.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;
import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.ApplyItem;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.enums.ApplyMode;
import com.jobwarden.apply.enums.ApplyStatus;
import com.jobwarden.apply.enums.CoverLetter;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.CvProject;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.OutreachDraft;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.cv.service.StoryService;
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
import com.jobwarden.inbox.dto.EmailUpdate;
import com.jobwarden.inbox.enums.UpdateDecider;
import com.jobwarden.inbox.enums.UpdateDecision;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.FitRequirement;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.InterviewBrief;
import com.jobwarden.jobs.dto.JobFit;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.NegotiationBrief;
import com.jobwarden.jobs.dto.NewTimelineEvent;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.TidyResult;
import com.jobwarden.jobs.enums.AppliedBy;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.RequirementImportance;
import com.jobwarden.jobs.enums.RequirementMatch;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.market.dto.MarketCountry;
import com.jobwarden.market.dto.MarketFigure;
import com.jobwarden.market.dto.MarketQuery;
import com.jobwarden.market.dto.MarketSalaries;
import com.jobwarden.market.dto.SurveyStatus;
import com.jobwarden.market.enums.MarketSource;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.mcp.dto.PostingCheck;
import com.jobwarden.mcp.enums.Verdict;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.exception.WatchlistErrorCode;
import com.jobwarden.watchlist.service.WatchlistService;
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
import org.springframework.test.json.JsonContent;
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
		PrepTools.class, FitTools.class, InboxTools.class, ProjectTools.class, WatchlistTools.class, OfferTools.class,
		LetterTools.class, StoryTools.class, Profiles.class })
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

	@MockitoBean
	private InterviewBriefService briefs;

	@MockitoBean
	private JobFitService fits;

	@MockitoBean
	private InboxService inbox;

	@MockitoBean
	private GitHubService github;

	@MockitoBean
	private WatchlistService watchlist;

	@MockitoBean
	private OfferService offers;

	@MockitoBean
	private MarketSalaryService market;

	@MockitoBean
	private CoverLetterService letters;

	@MockitoBean
	private StoryService stories;

	@BeforeEach
	void oneProfile() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));
		given(this.users.exists(1)).willReturn(true);
		// No CV yet, unless a test gives one: requests that need it hand out has_cv false
		given(this.cvs.find(anyLong())).willReturn(new Cv(null, CvDetails.NONE));
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
						"get_application_answers", "list_apply_queue", "start_apply", "report_apply_result", "watch_company",
						"list_offers", "get_market_salaries", "save_negotiation_brief", "get_cover_letter", "save_cover_letter",
						"save_outreach_draft", "list_stories", "save_stories");
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'update_job_status')].inputSchema.required[*]")
				.asArray()
				.containsExactly("job_id", "status");
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'list_jobs')].annotations.readOnlyHint")
				.asArray()
				.containsExactly(true);
		});
	}

	@Test
	void describesEachToolOnceAndBriefly() {
		assertThat(rpc("tools/list", "{}")).bodyJson().satisfies(body -> {
			// The title once, at the top: annotations keep only the hints clients decide approvals by
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'get_job')].title").asArray().containsExactly("Get a job");
			assertThat(body).extractingPath("$.result.tools[*].annotations.title").asArray().isEmpty();
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'check_postings')].annotations.openWorldHint")
				.asArray()
				.containsExactly(true);
			// Every tool's schema is read in every session: no description the name says, nothing the server ignores
			assertThat(body).extractingPath("$.result.tools[*].inputSchema.additionalProperties").asArray().isEmpty();
			assertThat(body).extractingPath("$.result.tools[?(@.name == 'get_job')].inputSchema.properties.profile_id")
				.asArray()
				.containsExactly(Map.of("type", "integer"));
			// Nor does any name one assistant
			assertThat(body.getJson()).doesNotContain("Claude");
		});
	}

	@Test
	void savesWhoSentAnApplicationTheAssistantByItsOldName() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)),
				Optional.of(job(ApplicationStatus.APPLIED)), Optional.of(job(ApplicationStatus.SAVED)),
				Optional.of(job(ApplicationStatus.APPLIED)), Optional.of(job(ApplicationStatus.SAVED)),
				Optional.of(job(ApplicationStatus.APPLIED)));

		callTool("update_job_status", "{\"job_id\": 7, \"status\": \"APPLIED\", \"applied_by\": \"ASSISTANT\"}");
		callTool("update_job_status", "{\"job_id\": 7, \"status\": \"APPLIED\"}");
		callTool("update_job_status", "{\"job_id\": 7, \"status\": \"APPLIED\", \"applied_by\": \"claude\"}");
		assertThat(callTool("update_job_status", "{\"job_id\": 7, \"status\": \"APPLIED\", \"applied_by\": \"ME\"}"))
			.bodyJson()
			.satisfies(body -> assertThat(body).extractingPath("$.result.isError").isEqualTo(true));

		ArgumentCaptor<NewTimelineEvent> events = ArgumentCaptor.forClass(NewTimelineEvent.class);
		then(this.applications).should(org.mockito.Mockito.times(3)).addEvent(eq(1L), eq(7L), events.capture());
		assertThat(events.getAllValues()).extracting(NewTimelineEvent::appliedBy)
			.containsExactly(AppliedBy.CLAUDE, AppliedBy.YOU, AppliedBy.CLAUDE);
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
			assertThat(result(body)).extractingPath("$.jobs[0].link").isEqualTo("https://jobs.lever.co/acme/7");
			// To the minute: seconds would only cost tokens
			assertThat(result(body)).extractingPath("$.jobs[0].last_modified").isEqualTo("2026-10-20T10:00Z");
			// Fields without a value are left out
			assertThat(result(body)).extractingPath("$.jobs[0]").asMap().doesNotContainKey("salary");
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.total")
			.isEqualTo(0));
	}

	@Test
	void movesAJobToAnotherStatus() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.APPLIED)),
				Optional.of(job(ApplicationStatus.POSITION_CLOSED)));

		assertThat(callTool("update_job_status",
				"{\"job_id\": 7, \"status\": \"POSITION_CLOSED\", \"note\": \"The posting is gone\"}"))
			.bodyJson()
			.satisfies(body -> {
				assertThat(result(body)).extractingPath("$.previous_status").isEqualTo("APPLIED");
				assertThat(result(body)).extractingPath("$.status").isEqualTo("POSITION_CLOSED");
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.follow_up.contact_email")
			.isEqualTo("ana@acme.example"));
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
			assertThat(result(body)).extractingPath("$.has_cv").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.fields.headline").isEqualTo("Senior Java Developer");
			assertThat(result(body)).extractingPath("$.fields.experience[0].company").isEqualTo("Acme");
			assertThat(result(body)).extractingPath("$.fields").asMap().doesNotContainKey("email");
			// Brief: the fields say it, so not the file's text again
			assertThat(result(body)).extractingPath("$.file").asMap().containsEntry("name", "cv.pdf").doesNotContainKey("text");
		});
		assertThat(callTool("get_cv", "{\"full\": true}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.file.text").isEqualTo("Java, Spring"));
	}

	@Test
	void leavesTheJobsPointsOutOfABriefCv() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.headline("Senior Java Developer")
			.experience(List.of(new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Built the payments API")))
			.build()));

		assertThat(callTool("get_cv", "{}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.fields.experience[0]").asMap()
				.containsEntry("role", "Java Developer")
				.doesNotContainKey("description"));
		assertThat(callTool("get_cv", "{\"full\": true}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.fields.experience[0].description")
				.isEqualTo("Built the payments API"));
	}

	@Test
	void handsOutARequestToPrepareForAnInterview() {
		given(this.discovery.startRequest(1, 31)).willReturn(AiRequest.builder()
			.id(31)
			.kind(AiRequestKind.PREPARE_INTERVIEW)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.INTERVIEWING)));
		given(this.stories.findAll(1)).willReturn(List.of(Story.builder()
			.id(3)
			.title("The Kafka migration")
			.result("3x the throughput")
			.skills(List.of("Kafka"))
			.build()));

		assertThat(callTool("get_request", "{\"request_id\": 31}")).bodyJson().satisfies(body -> {
			// What the steps need comes with them: no get_job, get_cv or list_stories to call
			assertThat(result(body)).extractingPath("$.job.id").isEqualTo(7);
			assertThat(result(body)).extractingPath("$.cv.has_cv").isEqualTo(false);
			assertThat(result(body)).extractingPath("$.stories[0].title").isEqualTo("The Kafka migration");
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("job is the posting", "short question", "save_interview_brief and job_id 7",
						"finish_request with request_id 31")
				.doesNotContain("get_job", "get_cv", "list_stories", "Claude");
		});
	}

	@Test
	void savesTheInterviewBrief() {
		given(this.briefs.save(eq(1L), eq(7L), any())).willAnswer(answer -> answer.getArgument(2));

		assertThat(callTool("save_interview_brief", """
				{"job_id": 7, "company": "Acme makes payment terminals for shops, 400 people in Cluj and Berlin.",
				 "role": "The payments API team; they look for Kafka and on-call experience.",
				 "questions": ["How would you make a payment idempotent?", "Tell us about an outage you handled."],
				 "stories": ["The Kafka migration at Globex: 3x the throughput, no data lost."],
				 "ask_them": ["What does on-call look like?"],
				 "sources": ["https://acme.example.com/about"]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.questions").isEqualTo(2);
			assertThat(result(body)).extractingPath("$.page").isEqualTo("/applications/7");
		});
		then(this.briefs).should().save(1, 7, InterviewBrief.builder()
			.company("Acme makes payment terminals for shops, 400 people in Cluj and Berlin.")
			.role("The payments API team; they look for Kafka and on-call experience.")
			.questions(List.of("How would you make a payment idempotent?", "Tell us about an outage you handled."))
			.stories(List.of("The Kafka migration at Globex: 3x the throughput, no data lost."))
			.askThem(List.of("What does on-call look like?"))
			.sources(List.of("https://acme.example.com/about"))
			.build());
	}

	@Test
	void savesTheRequirementsAndGivesBackTheScoreJobWardenWorkedOut() {
		given(this.fits.save(eq(1L), eq(7L), any(), any())).willAnswer(answer -> new JobFit(
				JobFit.score(answer.getArgument(3)), answer.getArgument(2), answer.getArgument(3), Instant.now()));

		assertThat(callTool("save_job_fit", """
				{"job_id": 7, "summary": "Strong on Java and Kafka; no Kubernetes.",
				 "requirements": [
				   {"text": "5+ years of Java", "importance": "MUST", "match": "MET", "note": "8 years"},
				   {"text": "Kafka", "importance": "MUST", "match": "MET"},
				   {"text": "Kubernetes", "importance": "NICE", "match": "MISSING"}]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.score").isEqualTo(4.4);
			assertThat(result(body)).extractingPath("$.verdict").isEqualTo("STRONG");
		});
		then(this.fits).should().save(1, 7, "Strong on Java and Kafka; no Kubernetes.", List.of(
				new FitRequirement("5+ years of Java", RequirementImportance.MUST, RequirementMatch.MET, "8 years"),
				new FitRequirement("Kafka", RequirementImportance.MUST, RequirementMatch.MET, null),
				new FitRequirement("Kubernetes", RequirementImportance.NICE, RequirementMatch.MISSING, null)));
	}

	@Test
	void refusesAFitWithTooFewRequirementsOrWithoutHowTheyreMet() {
		assertThat(callTool("save_job_fit", """
				{"job_id": 7, "requirements": [{"text": "Java", "importance": "MUST", "match": "MET"}]}
				""")).bodyJson().satisfies(body -> assertThat(body).extractingPath("$.result.isError").isEqualTo(true));
		assertThat(callTool("save_job_fit", """
				{"job_id": 7, "requirements": [{"text": "Java", "importance": "MUST"}, {"text": "Kafka", "importance": "MUST",
				 "match": "MET"}, {"text": "SQL", "importance": "NICE", "match": "MET"}]}
				""")).bodyJson().satisfies(body -> assertThat(body).extractingPath("$.result.content[0].text").asString()
			.contains("match"));
		then(this.fits).should(never()).save(anyLong(), anyLong(), any(), any());
	}

	@Test
	void handsOutARequestToScoreTheJobsWithNoScore() {
		given(this.discovery.startRequest(1, 33)).willReturn(AiRequest.builder()
			.id(33)
			.kind(AiRequestKind.SCORE_JOBS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		given(this.fits.findUnscored(1, RequestTools.SCORE_AT_ONCE)).willReturn(List.of(ApplicationSummary.builder()
			.id(9)
			.title("Kotlin Developer")
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.build()));

		assertThat(callTool("get_request", "{\"request_id\": 33}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.jobs_to_score[0].id").isEqualTo(9);
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("jobs_to_score", "get_job each", "MUST", "PARTIAL", "save_job_fit", "finish_request with request_id 33");
		});
	}

	@Test
	void listsTheEmailUpdatesWaitingWithTheJobTheyMatched() {
		given(this.inbox.findPending(1)).willReturn(List.of(EmailUpdate.builder()
			.id(5)
			.receivedAt(Instant.parse("2026-11-03T09:00:00Z"))
			.sender("Ana Pop <ana@acme.com>")
			.subject("Your application")
			.excerpt("We decided to move forward with other candidates.")
			.text("Dear Robert, " + "x".repeat(3000))
			.applicationId(7L)
			.jobTitle("Backend engineer")
			.company("Acme")
			.status(ApplicationStatus.REJECTED)
			.decision(UpdateDecision.PENDING)
			.build()));

		assertThat(callTool("list_email_updates", "{}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.updates[0].id").isEqualTo(5);
			assertThat(result(body)).extractingPath("$.updates[0].status").isEqualTo("REJECTED");
			assertThat(result(body)).extractingPath("$.updates[0].job.company").isEqualTo("Acme");
			assertThat(result(body)).extractingPath("$.updates[0].text").asString().hasSize(1500).endsWith("…");
		});
	}

	@Test
	void appliesOrDismissesAnEmailUpdateAsClaude() {
		assertThat(callTool("apply_email_update", "{\"update_id\": 5, \"job_id\": 8, \"status\": \"INTERVIEWING\"}"))
			.bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.job_id").isEqualTo(8));
		then(this.inbox).should().apply(1, 5, 8, ApplicationStatus.INTERVIEWING, null, UpdateDecider.CLAUDE);

		assertThat(callTool("apply_email_update", "{\"update_id\": 5, \"job_id\": 8, \"status\": \"SAVED\"}"))
			.bodyJson()
			.satisfies(body -> assertThat(body).extractingPath("$.result.isError").isEqualTo(true));

		callTool("dismiss_email_update", "{\"update_id\": 6}");
		then(this.inbox).should().dismiss(1, 6, UpdateDecider.CLAUDE);
	}

	@Test
	void handsOutARequestToSortTheInbox() {
		given(this.discovery.startRequest(1, 40)).willReturn(AiRequest.builder()
			.id(40)
			.kind(AiRequestKind.SORT_INBOX)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 40}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("list_email_updates with limit 50", "list_jobs with search", "apply_email_update",
						"dismiss_email_update", "Leave the ones you aren't sure of", "finish_request with request_id 40"));
	}

	@Test
	void listsTheGitHubProjectsChosenAndSavesTheCvsProjects() {
		given(this.github.findProjects(1)).willReturn(List.of(new GitHubProject("CRV96/jobwarden", "jobwarden",
				"https://github.com/CRV96/jobwarden", null, "A job tracker", List.of("Java", "HTML"), List.of("spring-boot"), 3,
				false, null, null, "# JobWarden")));
		given(this.cvs.saveProjects(eq(1L), any())).willAnswer(
				answer -> CvDetails.builder().projects(answer.getArgument(1)).build());

		assertThat(callTool("list_github_projects", "{}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.projects[0].url").isEqualTo("https://github.com/CRV96/jobwarden");
			assertThat(result(body)).extractingPath("$.projects[0].languages").asArray().containsExactly("Java", "HTML");
		});
		assertThat(callTool("save_cv_projects", """
				{"projects": [{"name": "JobWarden", "link": "https://github.com/CRV96/jobwarden",
				  "technologies": "Java, Spring Boot", "description": "A self-hosted job tracker."}]}
				""")).bodyJson().satisfies(body -> assertThat(result(body)).extractingPath("$.projects").isEqualTo(1));
		then(this.cvs).should().saveProjects(1, List.of(new CvProject("JobWarden", "https://github.com/CRV96/jobwarden",
				"Java, Spring Boot", "A self-hosted job tracker.")));
	}

	@Test
	void readsAReadmeWithoutItsBadgesImagesHtmlAndCode() {
		String readme = """
				# JobWarden [![CI](https://github.com/CRV96/jobwarden/actions/workflows/ci.yml/badge.svg)](https://github.com/CRV96/jobwarden/actions)

				<p align="center"><img src="docs/logo.png" width="120"></p>

				A self-hosted job tracker. ![screenshot](docs/screen.png)



				```bash
				docker compose up
				```
				It has 40,000 lines of Java.
				""";
		given(this.github.findProjects(1)).willReturn(List.of(new GitHubProject("CRV96/jobwarden", "jobwarden",
				"https://github.com/CRV96/jobwarden", null, null, List.of("Java"), List.of(), 3, false, null, null, readme)));

		assertThat(callTool("list_github_projects", "{}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.projects[0].readme")
				.isEqualTo("# JobWarden\n\nA self-hosted job tracker.\n\nIt has 40,000 lines of Java."));
		assertThat(ProjectTools.readme("x".repeat(ProjectTools.README_MAX_LENGTH + 10))).hasSize(ProjectTools.README_MAX_LENGTH);
		assertThat(ProjectTools.readme("![logo](logo.png)")).isNull();
	}

	@Test
	void handsOutTheCvsPointsWithoutTheFileSayingThemAgain() {
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("cv.pdf", "application/pdf", 84_000, 12, Instant.now()),
				CvDetails.builder()
					.headline("Senior Java Developer")
					.experience(List.of(new CvExperience("Java Developer", "Acme", null, "2021", "Present", "Built the payments API")))
					.build()));
		given(this.cvs.fileText(1)).willReturn(Optional.of("Senior Java Developer at Acme: built the payments API"));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));
		given(this.discovery.startRequest(1, 42)).willReturn(AiRequest.builder()
			.id(42)
			.kind(AiRequestKind.SCORE_JOB)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());
		given(this.discovery.startRequest(1, 43)).willReturn(AiRequest.builder()
			.id(43)
			.kind(AiRequestKind.TAILOR_CV)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 42}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.cv.fields.experience[0].description").isEqualTo("Built the payments API");
			assertThat(result(body)).extractingPath("$.cv.file").asMap().doesNotContainKey("text");
		});
		// Rewriting the CV reads both: the file may say what the fields don't
		assertThat(callTool("get_request", "{\"request_id\": 43}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.cv.file.text").asString().contains("payments API"));
	}

	@Test
	void handsOutARequestToWriteTheCvsProjects() {
		given(this.discovery.startRequest(1, 41)).willReturn(AiRequest.builder()
			.id(41)
			.kind(AiRequestKind.WRITE_PROJECTS)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 41}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("cv has the headline", "list_github_projects", "at most 6", "Never claim", "save_cv_projects"));
	}

	@Test
	void sendsTheResultOnceAsText() {
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of()));

		assertThat(callTool("list_jobs", "{}")).bodyJson().satisfies(body -> {
			assertThat(body).doesNotHavePath("$.result.structuredContent");
			assertThat(result(body)).extractingPath("$.total").isEqualTo(0);
		});
	}

	@Test
	void tellsWhichLinksAreNew() {
		given(this.discovery.findKnownLinks(eq(1L), any())).willReturn(Set.of("https://jobs.lever.co/acme/1"));
		given(this.hiring.findKnownLinks(eq(1L), any())).willReturn(Set.of("https://www.linkedin.com/in/ana"));

		assertThat(callTool("filter_links", """
				{"links": ["https://jobs.lever.co/acme/1", "https://www.linkedin.com/in/ana", "https://jobs.lever.co/acme/2"]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.new_links").asArray().containsExactly("https://jobs.lever.co/acme/2");
			assertThat(result(body)).extractingPath("$.known").isEqualTo(2);
		});
	}

	@Test
	void tidiesUpTheApplicationsWithNoAnswerInOneCall() {
		given(this.applications.tidyUnanswered(eq(1L), eq(RequestTools.NO_RESPONSE_AFTER_DAYS),
				eq(RequestTools.FOLLOW_UP_AFTER_DAYS), any())).willReturn(new TidyResult(List.of(4L, 5L), List.of(6L), 9));

		assertThat(callTool("tidy_applications", "{}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.moved_to_no_response").isEqualTo(2);
			assertThat(result(body)).extractingPath("$.follow_ups_set").isEqualTo(1);
			assertThat(result(body)).extractingPath("$.waiting_at_applied").isEqualTo(9);
		});
	}

	@Test
	void checksManyAddressesAtOnce() {
		given(this.checker.check("https://jobs.lever.co/acme/1")).willReturn(new PostingCheck("https://jobs.lever.co/acme/1",
				Verdict.OPEN, true, "The page has the posting's job data, still valid", 200, null, null));
		given(this.checker.check("https://jobs.lever.co/acme/2")).willReturn(new PostingCheck("https://jobs.lever.co/acme/2",
				Verdict.CLOSED, true, "The page is gone", 404, null, null));

		assertThat(callTool("check_postings", """
				{"urls": ["https://jobs.lever.co/acme/1", "https://jobs.lever.co/acme/2"]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.checked[*].verdict").asArray().containsExactly("OPEN", "CLOSED");
			assertThat(result(body)).extractingPath("$.checked[1]").asMap().doesNotContainKey("http_status");
		});
		then(this.applications).should(never()).addEvent(anyLong(), anyLong(), any());
	}

	@Test
	void saysWhenThereIsNoCv() {
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));

		assertThat(callTool("get_cv", "{}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$")
			.asMap()
			.containsEntry("has_cv", false)
			.doesNotContainKeys("fields", "file"));
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.requests[0].kind")
			.isEqualTo("FIND_JOBS_FOR_CV"));
		assertThat(callTool("get_request", "{\"request_id\": 12}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.requirements.locations[0]").isEqualTo("Romania");
			assertThat(result(body)).extractingPath("$.requirements.use_cv").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("From cv", "add_found_jobs and request_id 12, at most 10", "finish_request with request_id 12");
			assertThat(result(body)).extractingPath("$.cv.has_cv").isEqualTo(false);
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
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));

		assertThat(callTool("get_request", "{\"request_id\": 14}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.job.title").isEqualTo("Java Developer");
			assertThat(result(body)).doesNotHavePath("$.requirements");
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("job is the posting", "cv is the full CV", "never add a skill", "save_tailored_cv and job_id 7",
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
			assertThat(result(body)).extractingPath("$.title").isEqualTo("Backend engineer");
			assertThat(result(body)).extractingPath("$.jobs").isEqualTo(1);
			assertThat(result(body)).extractingPath("$.page").isEqualTo("/applications/7");
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.skipped[0]")
			.isEqualTo("https://example.com/2"));
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.added").isEqualTo(1));
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
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<>(List.of(ApplicationSummary.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.APPLIED)
			.capturedAt(Instant.parse("2026-10-20T10:00:00Z"))
			.build())));

		assertThat(callTool("get_request", "{\"request_id\": 22}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.requirements.titles[0]").isEqualTo("Java Developer");
			// The companies come with the request: no listing the jobs for them
			assertThat(result(body)).extractingPath("$.companies").asArray().containsExactly("Acme");
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("companies lists", "filter_links", "add_recruiters, at most 20", "finish_request with request_id 22")
				.doesNotContain("list_jobs");
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
			assertThat(result(body)).extractingPath("$.recruiters[0].profile_url")
				.isEqualTo("https://www.linkedin.com/in/ana");
			assertThat(result(body)).extractingPath("$.recruiters[0].followed").isEqualTo(true);
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
			assertThat(result(body)).extractingPath("$.contact.email").isEqualTo("robert@example.com");
			assertThat(result(body)).extractingPath("$.work_authorization").isEqualTo("EU citizen");
			assertThat(result(body)).extractingPath("$.needs_sponsorship").isEqualTo(false);
			assertThat(result(body)).extractingPath("$.cover_letter").isEqualTo("WRITE");
			assertThat(result(body)).extractingPath("$.custom[0].answer").isEqualTo("Yes, B");
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
			assertThat(result(body)).extractingPath("$.jobs[0].item_id").isEqualTo(3);
			assertThat(result(body)).extractingPath("$.jobs[0].mode").isEqualTo("SUBMIT");
			assertThat(result(body)).extractingPath("$.jobs[0].has_tailored_cv").isEqualTo(false);
			assertThat(result(body)).extractingPath("$.jobs[0].cv_url")
				.isEqualTo("http://localhost/claude/cv?profile=1&job=7");
			assertThat(result(body)).extractingPath("$.jobs[0].has_cover_letter").isEqualTo(false);
			assertThat(result(body)).extractingPath("$.jobs[0]").asMap().doesNotContainKey("cover_letter_url");
		});
	}

	@Test
	void givesTheCoverLetterToUploadWhenTheJobHasOne() {
		given(this.autoApply.findOpenItems(1)).willReturn(List.of(ApplyItem.builder()
			.id(3)
			.applicationId(7)
			.title("Backend engineer")
			.mode(ApplyMode.REVIEW)
			.status(ApplyStatus.QUEUED)
			.createdAt(Instant.now())
			.build()));
		given(this.letters.findLetter(1, 7)).willReturn(Optional.of(
				new com.jobwarden.cv.dto.CoverLetter(7, "Dear Ana,\n\nKind regards,", null, Instant.now())));
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));

		assertThat(callTool("list_apply_queue", "{}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.jobs[0].has_cover_letter").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.jobs[0].cover_letter_url")
				.isEqualTo("http://localhost/claude/cover-letter?profile=1&job=7");
		});
		assertThat(callTool("get_cover_letter", "{\"job_id\": 7}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.has_letter").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.body").isEqualTo("Dear Ana,\n\nKind regards,");
		});
	}

	@Test
	void savesACoverLetterOnlyForTheProfilesOwnJobs() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));
		given(this.letters.saveLetter(1, 7, "Dear hiring team,\n\nI build payment systems.\n\nKind regards,",
				"Led with Kafka.", null)).willReturn(new com.jobwarden.cv.dto.CoverLetter(7,
						"Dear hiring team,\n\nI build payment systems.\n\nKind regards,", "Led with Kafka.", Instant.now()));

		assertThat(callTool("save_cover_letter", """
				{"job_id": 7, "body": "Dear hiring team,\\n\\nI build payment systems.\\n\\nKind regards,", "notes": "Led with Kafka."}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.paragraphs").isEqualTo(3);
			assertThat(result(body)).extractingPath("$.pdf").isEqualTo("http://localhost/claude/cover-letter?profile=1&job=7");
		});
		assertThat(callTool("save_cover_letter", "{\"job_id\": 8, \"body\": \"Dear Ana,\"}")).bodyJson()
			.extractingPath("$.result.isError")
			.isEqualTo(true);
		then(this.letters).should(never()).saveLetter(eq(1L), eq(8L), any(), any(), any());
	}

	@Test
	void savesTheMessagesToSomeoneHiringAndRefusesANoteTooLongForLinkedIn() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.APPLIED)));
		given(this.letters.saveDraft(eq(1L), any(), any())).willAnswer(answer -> answer.<OutreachDraft>getArgument(1)
			.toBuilder()
			.id(5)
			.build());

		assertThat(callTool("save_outreach_draft", """
				{"job_id": 7, "name": "Ana Pop", "role": "Technical Recruiter at Acme",
				 "profile_link": "https://www.linkedin.com/in/anapop", "linkedin_note": "Hi Ana, I saw the Java role…",
				 "email_subject": "The Java Developer role", "email_body": "Dear Ana, …"}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.draft_id").isEqualTo(5);
			assertThat(result(body)).extractingPath("$.note_characters").isEqualTo(28);
		});
		ArgumentCaptor<OutreachDraft> draft = ArgumentCaptor.forClass(OutreachDraft.class);
		then(this.letters).should().saveDraft(eq(1L), draft.capture(), any());
		assertThat(draft.getValue().applicationId()).isEqualTo(7);
		assertThat(draft.getValue().recipientLink()).isEqualTo("https://www.linkedin.com/in/anapop");

		given(this.letters.saveDraft(eq(1L), any(), any())).willThrow(new BusinessRuleException(CvErrorCode.LINKEDIN_NOTE_TOO_LONG,
				"The LinkedIn note has 340 characters", "300", "340"));
		assertThat(callTool("save_outreach_draft", "{\"job_id\": 7, \"name\": \"Ana Pop\", \"linkedin_note\": \"…\"}"))
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
				assertThat(body).extractingPath("$.result.content[0].text").asString().contains("340 characters");
			});
	}

	@Test
	void handsOutARequestToWriteToThePeopleHiringWithWhomToWriteTo() {
		given(this.discovery.startRequest(1, 33)).willReturn(AiRequest.builder()
			.id(33)
			.kind(AiRequestKind.WRITE_OUTREACH)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.APPLIED)
			.followUp(FollowUp.builder().contactName("Ana Pop").contactEmail("ana@acme.example").build())
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.hiring.findRecruiters(1)).willReturn(List.of(Recruiter.builder()
			.id(4)
			.name("Ion Ionescu")
			.headline("Talent Partner at Acme")
			.profileUrl("https://www.linkedin.com/in/ionescu")
			.company("ACME")
			.origin(RecruiterOrigin.FOUND)
			.build(), Recruiter.builder()
			.id(5)
			.name("Someone Else")
			.profileUrl("https://www.linkedin.com/in/else")
			.company("Globex")
			.origin(RecruiterOrigin.FOUND)
			.build()));

		assertThat(callTool("get_request", "{\"request_id\": 33}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.recipients[*].name").asArray().containsExactly("Ana Pop", "Ion Ionescu");
			assertThat(result(body)).extractingPath("$.recipients[0].why").isEqualTo("the job's contact");
			assertThat(result(body)).extractingPath("$.recipients[0].email").isEqualTo("ana@acme.example");
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("save_outreach_draft with job_id 7", "at most 300 characters", "Never send anything");
		});
	}

	@Test
	void writesOnceToSomeoneWhosBothTheJobsContactAndARecruiter() {
		given(this.discovery.startRequest(1, 37)).willReturn(AiRequest.builder()
			.id(37)
			.kind(AiRequestKind.WRITE_OUTREACH)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.APPLIED)
			.followUp(FollowUp.builder().contactName("Ana Pop").contactEmail("ana@acme.example").build())
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.hiring.findRecruiters(1)).willReturn(List.of(Recruiter.builder()
			.id(4)
			.name(" ana  pop")
			.headline("Technical Recruiter at Acme")
			.profileUrl("https://www.linkedin.com/in/anapop")
			.company("Acme")
			.origin(RecruiterOrigin.FOUND)
			.build()));

		assertThat(callTool("get_request", "{\"request_id\": 37}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.recipients").asArray().hasSize(1);
			assertThat(result(body)).extractingPath("$.recipients[0].name").isEqualTo("Ana Pop");
			assertThat(result(body)).extractingPath("$.recipients[0].email").isEqualTo("ana@acme.example");
			assertThat(result(body)).extractingPath("$.recipients[0].profile_link").isEqualTo("https://www.linkedin.com/in/anapop");
			assertThat(result(body)).extractingPath("$.recipients[0].role").isEqualTo("Technical Recruiter at Acme");
		});
	}

	@Test
	void writesOnceToSomeoneWhomNeitherRecordGivesAnEmailOrARole() {
		given(this.discovery.startRequest(1, 38)).willReturn(AiRequest.builder()
			.id(38)
			.kind(AiRequestKind.WRITE_OUTREACH)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.APPLIED)
			.followUp(FollowUp.builder().contactName("Ana Pop").build())
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.hiring.findRecruiters(1)).willReturn(List.of(Recruiter.builder()
			.id(4)
			.name("Ana Pop")
			.profileUrl("https://www.linkedin.com/in/anapop")
			.company("Acme")
			.origin(RecruiterOrigin.FOUND)
			.build()));

		assertThat(callTool("get_request", "{\"request_id\": 38}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.recipients").asArray().hasSize(1);
			assertThat(result(body)).extractingPath("$.recipients[0].profile_link").isEqualTo("https://www.linkedin.com/in/anapop");
			assertThat(result(body)).extractingPath("$.recipients[0]").asMap().doesNotContainKeys("email", "role");
		});
	}

	@Test
	void neverReplacesALetterChangedByHandAfterItsRequestWasTakenBack() {
		Instant asked = Instant.now().minusSeconds(600);
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));
		given(this.discovery.findLatestRequest(1, AiRequestKind.WRITE_COVER_LETTER, 7)).willReturn(Optional.of(AiRequest.builder()
			.id(34)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.CANCELLED)
			.createdAt(asked)
			.build()));
		given(this.letters.findLetter(1, 7)).willReturn(Optional.of(
				new com.jobwarden.cv.dto.CoverLetter(7, "Dear Ana, as I wrote it myself.", null, Instant.now())));

		assertThat(callTool("save_cover_letter", "{\"job_id\": 7, \"body\": \"Dear Ana,\"}")).bodyJson().satisfies(body -> {
			assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
			assertThat(body).extractingPath("$.result.content[0].text").asString().contains("stays as it is");
		});
		then(this.letters).should(never()).saveLetter(eq(1L), eq(7L), any(), any(), any());
	}

	@Test
	void neverReplacesALetterWhoseRequestWasTakenBackButWritesOneForAJobWithout() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));
		given(this.discovery.findLatestRequest(1, AiRequestKind.WRITE_COVER_LETTER, 7)).willReturn(Optional.of(AiRequest.builder()
			.id(34)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.CANCELLED)
			.createdAt(Instant.now())
			.build()));
		// Unchanged since it was asked: taken back all the same
		given(this.letters.findLetter(1, 7)).willReturn(Optional.of(
				new com.jobwarden.cv.dto.CoverLetter(7, "Dear Ana,", null, Instant.now().minusSeconds(3600))));
		assertThat(callTool("save_cover_letter", "{\"job_id\": 7, \"body\": \"Dear Ana,\"}")).bodyJson()
			.extractingPath("$.result.isError")
			.isEqualTo(true);
		then(this.letters).should(never()).saveLetter(eq(1L), eq(7L), any(), any(), any());

		// With no letter, Auto-apply's goes in
		given(this.letters.findLetter(1, 7)).willReturn(Optional.empty());
		given(this.letters.saveLetter(1, 7, "Dear Ana,", null, null))
			.willReturn(new com.jobwarden.cv.dto.CoverLetter(7, "Dear Ana,", null, Instant.now()));
		assertThat(callTool("save_cover_letter", "{\"job_id\": 7, \"body\": \"Dear Ana,\"}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.job_id").isEqualTo(7));
	}

	@Test
	void savesClaudesLetterAndMessagesOnlyOverWhatWasntChangedSinceItWasAsked() {
		Instant asked = Instant.now().minusSeconds(60);
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.SAVED)));
		for (AiRequestKind kind : List.of(AiRequestKind.WRITE_COVER_LETTER, AiRequestKind.WRITE_OUTREACH)) {
			given(this.discovery.findLatestRequest(1, kind, 7)).willReturn(Optional.of(AiRequest.builder()
				.id(34)
				.kind(kind)
				.applicationId(7L)
				.requirements(AiRequirements.NONE)
				.status(AiRequestStatus.WORKING)
				.createdAt(asked)
				.build()));
		}
		given(this.letters.saveLetter(1, 7, "Dear Ana,", null, asked))
			.willReturn(new com.jobwarden.cv.dto.CoverLetter(7, "Dear Ana,", null, Instant.now()));
		given(this.letters.saveDraft(eq(1L), any(), eq(asked))).willAnswer(answer -> answer.<OutreachDraft>getArgument(1)
			.toBuilder()
			.id(5)
			.build());

		callTool("save_cover_letter", "{\"job_id\": 7, \"body\": \"Dear Ana,\"}");
		callTool("save_outreach_draft", "{\"job_id\": 7, \"name\": \"Ana Pop\"}");

		// The service keeps what the profile changed after the time it was asked (CoverLetterAndStoryServiceTests)
		then(this.letters).should().saveLetter(1, 7, "Dear Ana,", null, asked);
		then(this.letters).should().saveDraft(eq(1L), any(), eq(asked));
	}

	@Test
	void savesNoMoreMessagesOnceTheirRequestWasTakenBack() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(job(ApplicationStatus.APPLIED)));
		given(this.discovery.findLatestRequest(1, AiRequestKind.WRITE_OUTREACH, 7)).willReturn(Optional.of(AiRequest.builder()
			.id(36)
			.kind(AiRequestKind.WRITE_OUTREACH)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.CANCELLED)
			.createdAt(Instant.now())
			.build()));

		assertThat(callTool("save_outreach_draft", "{\"job_id\": 7, \"name\": \"Ana Pop\"}")).bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
				assertThat(body).extractingPath("$.result.content[0].text").asString().contains("took the request");
			});
		then(this.letters).should(never()).saveDraft(anyLong(), any(), any());
	}

	@Test
	void refusesMoreStoriesThanABriefNames() {
		String ids = java.util.stream.LongStream.rangeClosed(1, 21)
			.mapToObj(String::valueOf)
			.collect(java.util.stream.Collectors.joining(", "));

		assertThat(callTool("save_interview_brief", "{\"job_id\": 7, \"company\": \"Acme.\", \"story_ids\": [" + ids + "]}"))
			.bodyJson()
			.extractingPath("$.result.isError")
			.isEqualTo(true);
	}

	@Test
	void handsOutARequestToWriteACoverLetter() {
		given(this.discovery.startRequest(1, 34)).willReturn(AiRequest.builder()
			.id(34)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 34}")).bodyJson()
			.extractingPath("$.result.content[0].text")
			.asString()
			.contains("job is the posting", "cv is the full CV", "save_cover_letter and job_id 7", "no clichés",
					"base_letter");
	}

	@Test
	void givesClaudeTheBaseLetterFilledInForTheJob() {
		given(this.discovery.startRequest(1, 36)).willReturn(AiRequest.builder()
			.id(36)
			.kind(AiRequestKind.WRITE_COVER_LETTER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.letters.findBaseLetter(1)).willReturn(Optional.of(new com.jobwarden.cv.dto.BaseLetter(
				"Dear hiring team,\n\nI'd like to join {company} as {role}.", Instant.now())));

		assertThat(callTool("get_request", "{\"request_id\": 36}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.base_letter")
				.isEqualTo("Dear hiring team,\n\nI'd like to join Acme as Java Developer."));
		assertThat(callTool("get_cover_letter", "{\"job_id\": 7}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.has_letter").isEqualTo(false);
			assertThat(result(body)).extractingPath("$.base_letter")
				.isEqualTo("Dear hiring team,\n\nI'd like to join Acme as Java Developer.");
		});
	}

	@Test
	void handsOutARequestToNegotiateAnOfferWithTheMarketsFigures() {
		given(this.discovery.startRequest(1, 35)).willReturn(AiRequest.builder()
			.id(35)
			.kind(AiRequestKind.NEGOTIATE_OFFER)
			.applicationId(7L)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("get_request", "{\"request_id\": 35}")).bodyJson()
			.extractingPath("$.result.content[0].text")
			.asString()
			.contains("list_offers", "get_market_salaries", "save_negotiation_brief and job_id 7", "Never invent an offer");
	}

	@Test
	void listsTheOffersWithTheirTermsAndYearlySalaries() {
		ApplicationSummary acme = ApplicationSummary.builder()
			.id(7)
			.title("Java Developer")
			.company("Acme")
			.location("Berlin")
			.status(ApplicationStatus.OFFER)
			.capturedAt(Instant.now())
			.build();
		given(this.offers.compare(1, "EUR", SalaryPeriod.YEAR)).willReturn(OfferComparison.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.offers(List.of(new ComparedOffer(acme, JobOffer.builder().baseSalary("90,000 EUR").bonus("10%").build(),
					"90,000 EUR", new SalaryEntry(acme, new BigDecimal("90000.00"), new BigDecimal("90000.00"), "EUR", false,
							null), false, null, false)))
			.currencies(List.of("EUR"))
			.build());

		assertThat(callTool("list_offers", "{}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.offers[0].job_id").isEqualTo(7);
			assertThat(result(body)).extractingPath("$.offers[0].yearly_low").isEqualTo(90000);
			assertThat(result(body)).extractingPath("$.offers[0].bonus").isEqualTo("10%");
		});
	}

	@Test
	void givesWhatTheMarketPaysAndTheRolesItKnowsWhenItDoesntKnowTheOneAskedFor() {
		given(this.market.roles()).willReturn(List.of("Developer, back-end", "Developer, full-stack"));
		given(this.market.find(new MarketQuery("Backend dev", "Java", List.of("DE", "RO")), "EUR", SalaryPeriod.YEAR))
			.willReturn(MarketSalaries.builder()
				.role("Backend dev")
				.skill("Java")
				.currency("EUR")
				.period(SalaryPeriod.YEAR)
				.countries(List.of(new MarketCountry("DE", List.of(new MarketFigure(MarketSource.STACK_OVERFLOW, 2025,
						new BigDecimal("60000.4"), new BigDecimal("75000.6"), new BigDecimal("90000"), 120)))))
				.survey(new SurveyStatus(2025, 1000, Instant.now(), false, null))
				.adzunaSearch("java developer")
				.build());

		assertThat(callTool("get_market_salaries", """
				{"role": "Backend dev", "skill": "Java", "countries": ["de", "RO", "DE"]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.countries[0].figures[0].middle").isEqualTo(75001);
			assertThat(result(body)).extractingPath("$.survey_imported").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.roles").asArray().contains("Developer, back-end");
		});
	}

	@Test
	void savesTheNegotiationBrief() {
		given(this.offers.saveBrief(eq(1L), eq(7L), any())).willAnswer(answer -> answer.getArgument(2));

		assertThat(callTool("save_negotiation_brief", """
				{"job_id": 7, "position": "8% under the median in Germany.", "target": "95,000 EUR",
				 "leverage": ["Median 92,000 EUR (Stack Overflow 2025)"], "asks": ["A signing bonus"],
				 "email": "Dear Ana, …"}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.leverage").isEqualTo(1);
			assertThat(result(body)).extractingPath("$.page").isEqualTo("/applications/7");
		});
		then(this.offers).should().saveBrief(1, 7, NegotiationBrief.builder()
			.position("8% under the median in Germany.")
			.target("95,000 EUR")
			.leverage(List.of("Median 92,000 EUR (Stack Overflow 2025)"))
			.asks(List.of("A signing bonus"))
			.email("Dear Ana, …")
			.build());
	}

	@Test
	void listsTheStoryBankAndAddsTheStoriesDraftedFromTheCv() {
		Story kafka = Story.builder()
			.id(3)
			.title("The Kafka migration")
			.situation("Orders were lost at peaks.")
			.result("No order lost since.")
			.skills(List.of("Kafka"))
			.build();
		given(this.stories.findAll(1)).willReturn(List.of(kafka));
		given(this.stories.addDrafted(eq(1L), any())).willAnswer(answer -> answer.<List<Story>>getArgument(1).subList(0, 1));

		assertThat(callTool("list_stories", "{}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.stories[0].title").isEqualTo("The Kafka migration"));
		assertThat(callTool("save_stories", """
				{"stories": [{"title": "Halving checkout latency", "action": "Cached the prices.", "result": "[By how much?]"},
				             {"title": "The Kafka migration"}]}
				""")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.added").asArray().containsExactly("Halving checkout latency");
			assertThat(result(body)).extractingPath("$.left_out").isEqualTo(1);
		});
	}

	@Test
	void keepsOnlyTheProfilesOwnStoriesInAnInterviewBrief() {
		given(this.stories.findAll(1, List.of(3L, 99L))).willReturn(List.of(Story.builder().id(3).title("Kafka").build()));
		given(this.briefs.save(eq(1L), eq(7L), any())).willAnswer(answer -> answer.getArgument(2));

		assertThat(callTool("save_interview_brief", """
				{"job_id": 7, "company": "Acme.", "story_ids": [3, 99]}
				""")).bodyJson().satisfies(body -> assertThat(result(body)).extractingPath("$.bank_stories").isEqualTo(1));
		ArgumentCaptor<InterviewBrief> brief = ArgumentCaptor.forClass(InterviewBrief.class);
		then(this.briefs).should().save(eq(1L), eq(7L), brief.capture());
		assertThat(brief.getValue().storyIds()).containsExactly(3L);
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.status")
			.isEqualTo("NEEDS_ANSWER"));
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.instructions")
			.asString()
			.contains("Claude in Chrome", "get_application_answers", "list_apply_queue", "start_apply", "LinkedIn Easy Apply",
					"Never solve or get around a CAPTCHA", "NEEDS_ANSWER", "Mode REVIEW", "finish_request with request_id 30"));
	}

	@Test
	void watchesACompanysBoardAndTellsTheStepsOfFindingJobsWhichAreWatched() {
		WatchedCompany acme = WatchedCompany.builder()
			.id(5)
			.name("Acme")
			.ats(Ats.LEVER)
			.board("acme")
			.boardUrl("https://jobs.lever.co/acme")
			.addedAt(Instant.now())
			.build();
		given(this.watchlist.watch(1, "https://jobs.lever.co/acme/1a2b", null)).willReturn(new WatchResult(acme, true, 2));
		given(this.watchlist.watch(1, "https://www.linkedin.com/jobs/view/1", null)).willThrow(new BusinessRuleException(
				WatchlistErrorCode.NOT_A_BOARD_LINK, "Not a link to a Greenhouse, Lever or Ashby board"));
		given(this.watchlist.findCompanies(1)).willReturn(List.of(acme));
		given(this.discovery.startRequest(1, 35)).willReturn(AiRequest.builder()
			.id(35)
			.kind(AiRequestKind.RUN_SAVED_SEARCHES)
			.requirements(AiRequirements.NONE)
			.status(AiRequestStatus.WAITING)
			.createdAt(Instant.now())
			.build());

		assertThat(callTool("watch_company", "{\"link\": \"https://jobs.lever.co/acme/1a2b\"}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.company_id").isEqualTo(5);
			assertThat(result(body)).extractingPath("$.board_url").isEqualTo("https://jobs.lever.co/acme");
			assertThat(result(body)).extractingPath("$.added").isEqualTo(true);
			assertThat(result(body)).extractingPath("$.new_jobs").isEqualTo(2);
		});
		assertThat(callTool("watch_company", "{\"link\": \"https://www.linkedin.com/jobs/view/1\"}")).bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.result.isError").isEqualTo(true);
				assertThat(body).extractingPath("$.result.content[0].text").asString().contains("Greenhouse, Lever or Ashby");
			});
		assertThat(callTool("get_request", "{\"request_id\": 35}")).bodyJson().satisfies(body -> {
			// The names alone: JobWarden reads the boards
			assertThat(result(body)).extractingPath("$.watched_companies").asArray().containsExactly("Acme");
			assertThat(result(body)).extractingPath("$.instructions").asString()
				.contains("watched_companies' boards", "call watch_company");
		});
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.status")
			.isEqualTo("FAILED"));
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
	void checksTheNewestJobsFirstWhateverTheirStatus() {
		given(this.applications.findAll(1, ApplicationStatus.SAVED))
			.willReturn(List.of(summary(9, ApplicationStatus.SAVED, "https://a.example/9")));
		given(this.applications.findAll(1, ApplicationStatus.INTERVIEWING))
			.willReturn(List.of(summary(7, ApplicationStatus.INTERVIEWING, "https://a.example/7")));
		given(this.applications.findAll(1, ApplicationStatus.APPLIED))
			.willReturn(List.of(summary(8, ApplicationStatus.APPLIED, "https://a.example/8")));
		given(this.checker.check(any())).willAnswer(call -> new PostingCheck(call.getArgument(0), Verdict.OPEN, true,
				"Up", 200, null, null));

		assertThat(callTool("check_postings", "{}")).bodyJson()
			.satisfies(body -> assertThat(result(body)).extractingPath("$.checked[*].job_id").asArray().containsExactly(7, 8, 9));
	}

	@Test
	void closesTheJobsWhosePostingIsCertainlyGone() {
		// Each status apart, as those lists leave the archived jobs out; a rejected job isn't checked
		given(this.applications.findAll(1, ApplicationStatus.APPLIED))
			.willReturn(List.of(summary(7, ApplicationStatus.APPLIED, "https://a.example/7")));
		given(this.applications.findAll(1, ApplicationStatus.INTERVIEWING))
			.willReturn(List.of(summary(8, ApplicationStatus.INTERVIEWING, "https://a.example/8")));
		given(this.applications.findAll(1, ApplicationStatus.REJECTED))
			.willReturn(List.of(summary(9, ApplicationStatus.REJECTED, "https://a.example/9")));
		given(this.checker.check(any())).willAnswer(call -> new PostingCheck(call.getArgument(0), Verdict.CLOSED, true,
				"The page is gone (404)", 404, null, null));

		assertThat(callTool("check_postings", "{\"close_when_gone\": true}")).bodyJson().satisfies(body -> {
			assertThat(result(body)).extractingPath("$.summary.CLOSED").isEqualTo(2);
			assertThat(result(body)).extractingPath("$.checked[*].job_id").asArray().containsExactly(7, 8);
			assertThat(result(body)).extractingPath("$.checked[*].closed").asArray()
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
			.satisfies(body -> assertThat(result(body)).extractingPath("$.verdict")
			.isEqualTo("OPEN"));
		then(this.applications).should(never()).addEvent(anyLong(), anyLong(), any());
	}

	@Test
	void givesTheSavedSearchesWithQueriesToRun() {
		given(this.discovery.findSearches(1)).willReturn(List.of(new SavedSearch(3, "Remote Java",
				SearchCriteria.builder().titles(List.of("Java Developer")).sites(List.of("jobs.lever.co")).build(),
				Instant.now())));

		assertThat(callTool("list_saved_searches", "{}")).bodyJson().satisfies(body -> {
			// The queries alone, to run with a web search: their addresses would say them again
			assertThat(result(body)).extractingPath("$.searches[0].queries.GOOGLE")
				.isEqualTo("site:jobs.lever.co \"Java Developer\"");
			assertThat(result(body)).extractingPath("$.searches[0].queries.LINKEDIN").asString().contains("Java Developer");
			assertThat(result(body)).extractingPath("$.searches[0].criteria.titles[0]")
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
				assertThat(result(body)).extractingPath("$.added").isEqualTo(1);
				assertThat(result(body)).extractingPath("$.skipped[0].reason").isEqualTo("already found");
				assertThat(result(body)).extractingPath("$.waiting_for_a_decision").isEqualTo(4);
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

	/** A tool's result: the JSON its text holds, the only copy of it the server sends. */
	private static JsonContent result(JsonContent body) {
		return new JsonContent(JsonPath.<String>read(body.getJson(), "$.result.content[0].text"));
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
			// The higher the id, the earlier saved: a list newest first is in the ids' order
			.capturedAt(Instant.parse("2026-10-01T10:00:00Z").minusSeconds(id * 3600))
			.build();
	}

}
