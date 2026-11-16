package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.CvFilePreview;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.dto.CvProject;
import com.jobwarden.cv.dto.GitHubAccount;
import com.jobwarden.cv.dto.GitHubRepository;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import com.jobwarden.discover.dto.AiRequest;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.enums.AiRequestStatus;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import com.jobwarden.web.view.CvPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * With the real PDF renderer: the PDF is made from the same template as the preview.
 */
@WebMvcTest(CvController.class)
@Import(CvPdf.class)
class CvControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final CvDetails DETAILS = CvDetails.builder()
		.fullName("Robert Ciapă")
		.headline("Senior Java Developer")
		.email("robert@example.com")
		.linkedin("https://www.linkedin.com/in/robert")
		.location("București")
		.summary("Ten years of Java.")
		.skills(List.of("Java", "Kafka"))
		.experience(List.of(new CvExperience("Java Developer", "Acme", "Bucharest", "2021", "Present", "Payments")))
		.education(List.of(new CvEducation("BSc Computer Science", "University of Bucharest", null, "2014", "2018", null)))
		.languages(List.of(new CvLanguage("Polish", "B2 – łatwo")))
		.updatedAt(Instant.now())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private GitHubService github;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.cvs.find(1)).willReturn(new Cv(null, DETAILS));
	}

	@Test
	void showsTheFieldsAndThePreview() {
		assertThat(this.mvc.get().uri("/cv")).hasStatusOk()
			.hasViewName("cv/cv")
			.bodyText()
			.contains("value=\"Robert Ciapă\"", "name=\"experience[0].role\"", "value=\"Java Developer\"",
					"name=\"education[0].degree\"", "Java, Kafka", "name=\"languages[0].level\"",
					// The rows new ones are made from
					"name=\"experience[i].role\"", "data-add-row=\"certifications\"", "src=\"/cv/preview\"", "Upload");
	}

	@Test
	void savesTheFieldsWithTheirRows() {
		assertThat(this.mvc.post()
			.uri("/cv")
			.param("fullName", "Robert")
			.param("email", "robert@example.com")
			.param("skills", "Java, Kafka\nSpring")
			.param("experience[0].role", "Java Developer")
			.param("experience[0].company", "Acme")
			.param("experience[1].role", "Intern")
			.param("languages[0].language", "Romanian")
			.param("languages[0].level", "Native")).hasRedirectedUrl("/cv?saved");

		ArgumentCaptor<CvDetails> saved = ArgumentCaptor.captor();
		then(this.cvs).should().saveDetails(eq(1L), saved.capture());
		assertThat(saved.getValue().fullName()).isEqualTo("Robert");
		assertThat(saved.getValue().skills()).containsExactly("Java", " Kafka", "Spring");
		assertThat(saved.getValue().experience()).extracting(CvExperience::role).containsExactly("Java Developer", "Intern");
		assertThat(saved.getValue().languages()).containsExactly(new CvLanguage("Romanian", "Native"));
	}

	@Test
	void showsTheFieldsAgainWithWhatsWrong() {
		assertThat(this.mvc.post().uri("/cv").param("fullName", "Robert").param("email", "robert at example")).hasStatusOk()
			.bodyText()
			.contains("The CV’s email isn’t an email address.", "value=\"robert at example\"");
		then(this.cvs).should(never()).saveDetails(anyLong(), any());
	}

	@Test
	void keepsTheUploadedFile() {
		MockMultipartFile file = new MockMultipartFile("file", "cv.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));

		assertThat(this.mvc.post().multipart().uri("/cv/file").file(file)).hasRedirectedUrl("/cv?uploaded");
		then(this.cvs).should().uploadFile(1, "cv.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void saysWhyAFileWasntKept() {
		willThrow(new BusinessRuleException(CvErrorCode.FILE_TYPE_NOT_SUPPORTED, "No", ".png")).given(this.cvs)
			.uploadFile(anyLong(), any(), any(), any());
		MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1 });

		assertThat(this.mvc.post().multipart().uri("/cv/file").file(file)).hasStatusOk()
			.bodyText()
			.contains("A CV can be a PDF, a Word document (.docx) or a text file, not .png.");
	}

	@Test
	void showsTheUploadedFileAndWhatClaudeCanRead() {
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("CV Robert.pdf", "application/pdf", 84_000, 4512, Instant.now()),
				DETAILS));

		assertThat(this.mvc.get().uri("/cv")).bodyText()
			.contains("CV Robert.pdf", "83 KB", "The assistant can read 4,512 characters of it.", "href=\"/cv/file\"", "Replace");
	}

	@Test
	void downloadsTheFileAsItWas() {
		given(this.cvs.downloadFile(1)).willReturn(new CvFileContent("CV Robert.pdf", "application/pdf", new byte[] { 37, 80 }));

		assertThat(this.mvc.get().uri("/cv/file")).hasStatusOk()
			.hasContentType("application/pdf")
			.headers()
			.hasValue("Content-Disposition", "attachment; filename=\"CV Robert.pdf\"; filename*=UTF-8''CV%20Robert.pdf");
	}

	@Test
	void previewsTheUploadedFileBesideTheBuiltCv() {
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("CV Robert.docx", "application/msword", 84_000, 4512, Instant.now()),
				DETAILS));

		assertThat(this.mvc.get().uri("/cv")).bodyText()
			.contains("name=\"cv-preview\" value=\"built\" data-preview-tab checked=\"checked\"", "Your file",
					"src=\"/cv/preview\"", "src=\"/cv/file/preview\"", "data-preview-for=\"file\"");
	}

	@Test
	void previewsTheFileFirstWhenTheFieldsAreEmpty() {
		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("CV Robert.pdf", "application/pdf", 84_000, 4512, Instant.now()),
				CvDetails.builder().build()));

		assertThat(this.mvc.get().uri("/cv")).bodyText()
			.contains("name=\"cv-preview\" value=\"file\" data-preview-tab checked=\"checked\"");
	}

	@Test
	void showsAWordDocumentAsHtml() {
		given(this.cvs.previewFile(1)).willReturn(new CvFilePreview("CV Robert.docx", false,
				"<h1>Robert Ciapă</h1><p>&lt;script&gt;</p>", null));

		assertThat(this.mvc.get().uri("/cv/file/preview")).hasStatusOk()
			.hasViewName("cv/file-preview")
			.bodyText()
			.contains("<title>CV Robert.docx</title>", "<h1>Robert Ciapă</h1><p>&lt;script&gt;</p>",
					"its own fonts, layout and images aren’t in it");
	}

	@Test
	void showsATextFileAsItsText() {
		given(this.cvs.previewFile(1)).willReturn(new CvFilePreview("cv.txt", false, null, "Robert <Java>"));

		assertThat(this.mvc.get().uri("/cv/file/preview")).bodyText().contains("<pre class=\"text\">Robert &lt;Java&gt;</pre>");
	}

	@Test
	void letsTheBrowserShowAPdf() {
		given(this.cvs.previewFile(1)).willReturn(new CvFilePreview("CV Robert.pdf", true, null, null));
		given(this.cvs.downloadFile(1)).willReturn(new CvFileContent("CV Robert.pdf", "application/pdf", new byte[] { 37, 80 }));

		assertThat(this.mvc.get().uri("/cv/file/preview")).hasRedirectedUrl("/cv/file/inline");
		assertThat(this.mvc.get().uri("/cv/file/inline")).hasStatusOk()
			.hasContentType("application/pdf")
			.headers()
			.hasValue("Content-Disposition", "inline; filename=\"CV Robert.pdf\"; filename*=UTF-8''CV%20Robert.pdf")
			.hasValue("X-Content-Type-Options", "nosniff");
	}

	@Test
	void downloadsAnyOtherFileItsAskedToShow() {
		given(this.cvs.downloadFile(1)).willReturn(new CvFileContent("cv.html", "text/html", new byte[] { 60 }));

		assertThat(this.mvc.get().uri("/cv/file/inline")).headers()
			.hasValue("Content-Disposition", "attachment; filename=\"cv.html\"; filename*=UTF-8''cv.html");
	}

	@Test
	void offersToConnectGitHub() {
		assertThat(this.mvc.get().uri("/cv")).bodyText().contains("hx-get=\"/cv/github\" hx-trigger=\"load\"");
		assertThat(this.mvc.get().uri("/cv/github").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("name=\"username\"", "name=\"token\"", "fine-grained token", "lists your public repositories", "only ever sent to GitHub");
	}

	@Test
	void showsTheRepositoriesToChooseFrom() {
		given(this.github.findAccount(1)).willReturn(Optional.of(new GitHubAccount("CRV96", true, List.of("CRV96/jobwarden"),
				Instant.now())));
		given(this.github.listRepositories(1)).willReturn(List.of(
				new GitHubRepository("CRV96/jobwarden", "jobwarden", "A job tracker", "Java", 3, false, true, null, "https://github.com/CRV96/jobwarden"),
				new GitHubRepository("CRV96/dotfiles", "dotfiles", null, null, 0, true, false, null, "https://github.com/CRV96/dotfiles")));

		assertThat(this.mvc.get().uri("/cv/github").header("HX-Request", "true")).bodyText()
			.contains("@CRV96", "With your token", "value=\"CRV96/jobwarden\" checked=\"checked\"", "value=\"CRV96/dotfiles\">",
					"★ 3", "Fork", "Tick up to 12 for your CV", "from the 1 repositories you chose",
					"Write my CV’s projects from GitHub");
	}

	@Test
	void connectsGitHubOrSaysWhyNotWithoutTheToken() {
		willThrow(new BusinessRuleException(CvErrorCode.GITHUB_TOKEN_REFUSED, "refused")).given(this.github)
			.connect(1, "CRV96", "github_pat_secret");

		assertThat(this.mvc.post().uri("/cv/github").param("username", "CRV96").param("token", "github_pat_secret")
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("GitHub refused the token", "value=\"CRV96\"")
			.doesNotContain("github_pat_secret");
	}

	@Test
	void keepsTheRepositoriesChosenButNotTooMany() {
		assertThat(this.mvc.post().uri("/cv/github/repositories").param("repository", "CRV96/jobwarden", "CRV96/site")
			.header("HX-Request", "true")).hasStatusOk();
		then(this.github).should().chooseRepositories(1, List.of("CRV96/jobwarden", "CRV96/site"));

		assertThat(this.mvc.post().uri("/cv/github/repositories")
			.param("repository", IntStream.range(0, 13).mapToObj(number -> "CRV96/r" + number).toArray(String[]::new))
			.header("HX-Request", "true")).bodyText().contains("Choose up to 12 repositories.");
		then(this.github).should(never()).chooseRepositories(eq(1L), argThat(chosen -> chosen.size() > 12));
	}

	@Test
	void asksClaudeToWriteTheProjects() {
		given(this.github.findAccount(1)).willReturn(Optional.of(new GitHubAccount("CRV96", false, List.of("CRV96/jobwarden"),
				Instant.now())));
		given(this.discovery.findLatestRequest(1, AiRequestKind.WRITE_PROJECTS)).willReturn(Optional.empty())
			.willReturn(Optional.of(new AiRequest(11, AiRequestKind.WRITE_PROJECTS, AiRequirements.NONE,
					AiRequestStatus.WAITING, null, 0, Instant.now(), null, null, null)));

		assertThat(this.mvc.post().uri("/cv/github/write").header("HX-Request", "true")).bodyText()
			.contains("Do JobWarden request 11", "hx-trigger=\"every 5s\"");
		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_PROJECTS, AiRequirements.NONE);
	}

	@Test
	void savesAndShowsTheProjects() {
		assertThat(this.mvc.post()
			.uri("/cv")
			.param("fullName", "Robert")
			.param("projects[0].name", "JobWarden")
			.param("projects[0].link", "https://github.com/CRV96/jobwarden")
			.param("projects[0].technologies", "Java")).hasRedirectedUrl("/cv?saved");
		ArgumentCaptor<CvDetails> saved = ArgumentCaptor.captor();
		then(this.cvs).should().saveDetails(eq(1L), saved.capture());
		assertThat(saved.getValue().projects()).containsExactly(
				new CvProject("JobWarden", "https://github.com/CRV96/jobwarden", "Java", null));
	}

	@Test
	void previewsTheCvBuiltFromTheFields() {
		assertThat(this.mvc.get().uri("/cv/preview")).hasStatusOk()
			.hasViewName("cv/document")
			.bodyText()
			.contains("<h1>Robert Ciapă</h1>", "Senior Java Developer", "linkedin.com/in/robert", "Experience",
					"2021 – Present", "University of Bucharest", ">Kafka<")
			.doesNotContain("<aside");
	}

	@Test
	void makesAPdfOfTheCv() {
		assertThat(this.mvc.get().uri("/cv/pdf")).hasStatusOk()
			.hasContentType("application/pdf")
			.satisfies(result -> assertThat(result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII)))
			.headers()
			// The name with its ă, and without it for browsers that read only the first
			.hasValue("Content-Disposition", "attachment; filename=\"CV Robert Ciapa.pdf\"; filename*=UTF-8''CV%20Robert%20Ciap%C4%83.pdf");
	}

	@Test
	void sendsTheBrowserToTheProfilePickerWhenNoProfileIsSelected() {
		given(this.currentUser.get()).willReturn(Optional.empty());

		assertThat(this.mvc.get().uri("/cv")).hasRedirectedUrl("/profiles");
	}

}
