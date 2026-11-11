package com.jobwarden.mcp.controller;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.BaseLetter;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvCertification;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.CvProject;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.CoverLetterService;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.cv.service.StoryService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.dto.FoundJob;
import com.jobwarden.discover.dto.SavedSearch;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.enums.Decision;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.service.InboxService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.dto.ApplicationSummary;
import com.jobwarden.jobs.dto.ComparedOffer;
import com.jobwarden.jobs.dto.FollowUp;
import com.jobwarden.jobs.dto.JobOffer;
import com.jobwarden.jobs.dto.OfferComparison;
import com.jobwarden.jobs.dto.SalaryEntry;
import com.jobwarden.jobs.dto.TimelineEvent;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.enums.InterviewFormat;
import com.jobwarden.jobs.enums.SalaryPeriod;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.InterviewBriefService;
import com.jobwarden.jobs.service.JobFitService;
import com.jobwarden.jobs.service.OfferService;
import com.jobwarden.market.service.MarketSalaryService;
import com.jobwarden.search.SearchCriteria;
import com.jobwarden.search.WorkType;
import com.jobwarden.watchlist.dto.WatchedCompany;
import com.jobwarden.watchlist.enums.Ats;
import com.jobwarden.watchlist.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * What the assistant reads, in characters, measured on a profile like a real one: tools/list, the server's
 * instructions, and for each kind of request the reads its steps make. Prints a table, writes it to
 * target/mcp-budget.tsv, and fails when it grows past the budgets below.
 * <p>
 * Before the requests came with what they need, and the tools were described for any assistant, the same profile cost
 * tools/list 51,487 bytes (43,585 characters the model reads), and the 18 kinds of request 172,701 characters in 41
 * calls before their work began; after, 31,791 bytes (25,166), and 126,876 characters in 21 calls.
 */
@WebMvcTest(McpController.class)
@Import({ JobTools.class, DiscoverTools.class, CvTools.class, RequestTools.class, HiringTools.class, ApplyTools.class,
		PrepTools.class, FitTools.class, InboxTools.class, ProjectTools.class, WatchlistTools.class, OfferTools.class,
		LetterTools.class, StoryTools.class, Profiles.class })
class McpBudgetTests {

	private static final long JOB = 7;

	/** What the model reads of tools/list, in every session of every assistant: names, descriptions, schemas. */
	private static final int TOOLS_BUDGET = 26_000;

	/**
	 * The most a request may hand out: Codex cuts a tool's result to 10,000 tokens, some 35,000 characters of JSON, and
	 * the profile's own CV and posting may be longer than these.
	 */
	private static final int REQUEST_BUDGET = 25_000;

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
	void aProfileLikeARealOne() {
		Instant now = Instant.parse("2026-11-03T09:56:14.041461Z");
		given(this.users.findAll()).willReturn(List.of(new User(1, "Robert", now)));
		given(this.users.exists(1)).willReturn(true);
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("CV.pdf", "application/pdf", 90_000, 6000, now), cv(now)));
		given(this.cvs.fileText(1)).willReturn(Optional.of(text(6000)));
		given(this.applications.findById(eq(1L), eq(JOB))).willReturn(Optional.of(job(now)));
		given(this.applications.find(eq(1L), any())).willReturn(new PageImpl<ApplicationSummary>(IntStream.range(0, 20)
			.mapToObj(i -> ApplicationSummary.builder()
				.id(100 + i)
				.title("Senior Java Developer " + i)
				.company("Company " + i)
				.location("Bucharest, Romania")
				.link("https://jobs.lever.co/company" + i + "/0f6e2d1c-3b4a-4c5d-8e9f-0a1b2c3d4e5f")
				.source("jobs.lever.co")
				.status(ApplicationStatus.SAVED)
				.capturedAt(now)
				.build())
			.toList()));
		given(this.discovery.findDecided(anyLong(), anyInt())).willReturn(IntStream.range(0, 30)
			.mapToObj(i -> FoundJob.builder()
				.id(i)
				.title("Backend Engineer (Java) " + i)
				.company("Company " + i)
				.link("https://example.com/" + i)
				.decision((i % 2 == 0) ? Decision.SAVED : Decision.DISCARDED)
				.foundAt(now)
				.build())
			.toList());
		given(this.watchlist.findCompanies(1)).willReturn(IntStream.range(0, 15)
			.mapToObj(i -> new WatchedCompany(i, "Company " + i, Ats.GREENHOUSE, "company" + i,
					"https://boards.greenhouse.io/company" + i, now, now, null, 0))
			.toList());
		given(this.hiring.findRecruiters(1)).willReturn(IntStream.range(0, 12)
			.mapToObj(i -> new Recruiter(i, "Recruiter Name " + i, "Technical Recruiter at Company " + i,
					"https://www.linkedin.com/in/recruiter-name-" + i, "Company " + i, "RO", null, RecruiterOrigin.FOUND,
					i < 8, 2, List.of()))
			.toList());
		given(this.stories.findAll(1)).willReturn(IntStream.range(0, 5)
			.mapToObj(i -> new Story(i, "Cutting checkout latency in half " + i, text(250), text(150), text(400),
					text(200), List.of("Kafka", "Leadership"), now))
			.toList());
		given(this.discovery.findSearches(1)).willReturn(List.of(
				new SavedSearch(3, "Remote Java in Europe", SearchCriteria.builder()
					.titles(List.of("Java Developer", "Backend Engineer"))
					.locations(List.of("Europe", "Romania"))
					.skills(List.of("Spring Boot", "Kafka"))
					.sites(List.of("jobs.lever.co", "boards.greenhouse.io", "jobs.ashbyhq.com"))
					.workTypes(Set.of(WorkType.REMOTE))
					.excludes(List.of("junior"))
					.build(), now),
				new SavedSearch(4, "Kotlin in Germany", SearchCriteria.builder()
					.titles(List.of("Kotlin Developer"))
					.locations(List.of("Germany"))
					.sites(List.of("jobs.lever.co", "boards.greenhouse.io", "jobs.ashbyhq.com"))
					.build(), now)));
		given(this.fits.findUnscored(anyLong(), anyInt())).willReturn(List.of());
		given(this.github.findProjects(1)).willReturn(IntStream.range(0, 6)
			.mapToObj(i -> new GitHubProject("robert/project-" + i, "project-" + i, "https://github.com/robert/project-" + i,
					null, "A tool that does something useful " + i, List.of("Java", "TypeScript"), List.of("spring-boot"), 3,
					false, now, now, text(1500)))
			.toList());
		ApplicationSummary offer = ApplicationSummary.builder()
			.id(JOB)
			.title("Senior Java Developer")
			.company("Acme")
			.location("Bucharest")
			.status(ApplicationStatus.OFFER)
			.capturedAt(now)
			.build();
		given(this.offers.compare(1, "EUR", SalaryPeriod.YEAR)).willReturn(OfferComparison.builder()
			.currency("EUR")
			.period(SalaryPeriod.YEAR)
			.offers(List.of(new ComparedOffer(offer, JobOffer.builder().baseSalary("70,000 EUR").bonus("10%").build(),
					"70,000 EUR", new SalaryEntry(offer, new BigDecimal("70000.00"), new BigDecimal("70000.00"), "EUR", false,
							null), false, null, false)))
			.currencies(List.of("EUR"))
			.build());
		given(this.letters.findBaseLetter(1)).willReturn(Optional.of(new BaseLetter(text(1800), now)));
		for (AiRequestKind kind : AiRequestKind.values()) {
			long id = 50 + kind.ordinal();
			given(this.discovery.startRequest(1, id)).willReturn(AiRequest.builder()
				.id(id)
				.kind(kind)
				.applicationId(kind.isAboutAJob() ? JOB : null)
				.requirements(kind.hasRequirements() ? AiRequirements.builder()
					.titles(List.of("Senior Java Developer", "Backend Engineer"))
					.locations(List.of("Romania", "Remote Europe"))
					.skills(List.of("Java", "Spring Boot", "Kafka"))
					.workTypes(Set.of(WorkType.REMOTE, WorkType.HYBRID))
					.useCv(kind == AiRequestKind.FIND_JOBS)
					.maxJobs(15)
					.build() : AiRequirements.NONE)
				.status(AiRequestStatus.WAITING)
				.createdAt(now)
				.build());
		}
	}

	/**
	 * The reads each kind's steps make before the work itself, besides get_request, which hands out the job and the CV
	 * with the request: as the tools and their arguments.
	 */
	private static Map<AiRequestKind, List<String[]>> reads() {
		Map<AiRequestKind, List<String[]>> reads = new LinkedHashMap<>();
		for (AiRequestKind kind : AiRequestKind.values()) {
			reads.put(kind, List.of());
		}
		reads.put(AiRequestKind.WRITE_PROJECTS, List.<String[]>of(new String[] { "list_github_projects", "{}" }));
		reads.put(AiRequestKind.NEGOTIATE_OFFER, List.<String[]>of(new String[] { "list_offers", "{}" }));
		return reads;
	}

	@Test
	void measuresWhatTheAssistantReads() throws Exception {
		List<String> lines = new ArrayList<>();
		String tools = body("tools/list", "{}");
		String modelFacing = modelFacing(tools);
		lines.add("tools/list (bytes)\t" + tools.length());
		lines.add("tools/list (what the model reads: names, descriptions, schemas)\t" + modelFacing.length());
		lines.add("tools\t" + JsonPath.<List<?>>read(tools, "$.result.tools").size());
		lines.add("instructions\t" + JsonPath.<String>read(body("initialize", "{\"protocolVersion\": \"2025-06-18\"}"),
				"$.result.instructions").length());
		lines.add("get_cv (brief)\t" + text("get_cv", "{}").length());
		lines.add("get_cv (full)\t" + text("get_cv", "{\"full\": true}").length());
		lines.add("get_job\t" + text("get_job", "{\"job_id\": " + JOB + "}").length());
		lines.add("list_jobs (20 jobs)\t" + text("list_jobs", "{}").length());
		lines.add("list_saved_searches (2)\t" + text("list_saved_searches", "{}").length());
		lines.add("list_github_projects (6)\t" + text("list_github_projects", "{}").length());
		lines.add("list_stories (5)\t" + text("list_stories", "{}").length());
		lines.add("kind\tget_request\treads before the work\tcalls (get_request + reads)");
		int total = 0;
		int calls = 0;
		Map<AiRequestKind, Integer> requests = new LinkedHashMap<>();
		for (Map.Entry<AiRequestKind, List<String[]>> kind : reads().entrySet()) {
			String request = text("get_request", "{\"request_id\": " + (50 + kind.getKey().ordinal()) + "}");
			requests.put(kind.getKey(), request.length());
			int read = request.length();
			for (String[] call : kind.getValue()) {
				read += text(call[0], call[1]).length();
			}
			total += read;
			calls += 1 + kind.getValue().size();
			lines.add(kind.getKey() + "\t" + request.length() + "\t" + read + "\t" + (1 + kind.getValue().size()));
		}
		lines.add("all kinds\t\t" + total + "\t" + calls);
		lines.forEach(System.out::println);
		Files.write(Path.of("target", "mcp-budget.tsv"), lines);

		assertThat(modelFacing).hasSizeLessThan(TOOLS_BUDGET);
		assertThat(requests).allSatisfy((kind, length) -> assertThat(length).as(kind.name()).isLessThan(REQUEST_BUDGET));
		assertThat(calls).isLessThanOrEqualTo(21);
	}

	/** What a client hands the model of each tool: its name, description and input schema. */
	private static String modelFacing(String toolList) {
		List<Map<String, Object>> tools = JsonPath.read(toolList, "$.result.tools");
		StringBuilder text = new StringBuilder();
		for (Map<String, Object> tool : tools) {
			text.append(tool.get("name")).append(tool.get("description"));
			text.append(net.minidev.json.JSONValue.toJSONString(tool.get("inputSchema")));
		}
		return text.toString();
	}

	private String text(String tool, String arguments) throws Exception {
		String body = body("tools/call", "{\"name\": \"" + tool + "\", \"arguments\": " + arguments + "}");
		return JsonPath.read(body, "$.result.content[0].text");
	}

	private String body(String method, String params) throws Exception {
		return this.mvc.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + method + "\", \"params\": " + params + "}")
			.exchange()
			.getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

	private static CvDetails cv(Instant now) {
		return CvDetails.builder()
			.fullName("Robert Example")
			.email("robert@example.com")
			.headline("Senior Backend Engineer, Java and Spring Boot")
			.location("Bucharest, Romania")
			.summary(text(450))
			.skills(List.of("Java", "Spring Boot", "Kafka", "PostgreSQL", "Docker", "Kubernetes", "AWS", "Terraform", "Redis",
					"gRPC", "REST", "Microservices", "JUnit", "Testcontainers", "Gradle", "Maven", "Git", "CI/CD"))
			.experience(IntStream.range(0, 4)
				.mapToObj(i -> new CvExperience("Senior Java Developer", "Company " + i, "Bucharest", "Mar 20" + (10 + 3 * i),
						"Feb 20" + (13 + 3 * i), String.join("\n", text(140), text(150), text(130), text(160), text(120))))
				.toList())
			.projects(IntStream.range(0, 3)
				.mapToObj(i -> new CvProject("Project " + i, "https://github.com/robert/project-" + i, "Java, Spring Boot",
						text(220)))
				.toList())
			.education(List.of(new CvEducation("BSc Computer Science", "University of Bucharest", "Bucharest", "2006",
					"2010", null)))
			.languages(List.of(new CvLanguage("Romanian", "Native"), new CvLanguage("English", "C1"),
					new CvLanguage("German", "A2")))
			.certifications(List.of(new CvCertification("AWS Certified Developer", "Amazon", "2023")))
			.updatedAt(now)
			.build();
	}

	private static ApplicationDetails job(Instant now) {
		return ApplicationDetails.builder()
			.id(JOB)
			.title("Senior Java Developer")
			.company("Acme")
			.location("Bucharest, Romania (Hybrid)")
			.salary("60,000–80,000 EUR per year")
			.employmentType("Full-time")
			.link("https://jobs.lever.co/acme/0f6e2d1c-3b4a-4c5d-8e9f-0a1b2c3d4e5f")
			.source("jobs.lever.co")
			.description(text(3500))
			.followUp(FollowUp.builder().contactName("Ana Pop").contactRole("Technical Recruiter").build())
			.status(ApplicationStatus.INTERVIEWING)
			.capturedAt(now)
			.timeline(List.of(
					new TimelineEvent(1, LocalDate.of(2026, 10, 20), LocalTime.of(10, 0), ApplicationStatus.SAVED, null, null,
							null, null, null, null),
					new TimelineEvent(2, LocalDate.of(2026, 10, 22), LocalTime.of(9, 30), ApplicationStatus.APPLIED, null,
							"Applied on Lever", null, null, null, null),
					new TimelineEvent(3, LocalDate.of(2026, 11, 2), LocalTime.of(14, 0), ApplicationStatus.INTERVIEWING,
							"Technical interview", null, null, InterviewFormat.VIDEO, "https://meet.example.com/abc", 60)))
			.build();
	}

	/** Words, like a CV's or a posting's, this many characters long. */
	private static String text(int length) {
		String words = "Designed and built the payments API in Java 21 and Spring Boot, cutting p99 latency by 40% for "
				+ "2 million users; led a team of five engineers through the migration to Kafka and Kubernetes. ";
		return words.repeat(length / words.length() + 1).substring(0, length).strip();
	}

}
