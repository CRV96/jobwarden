package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvEducation;
import com.jobwarden.cv.dto.CvExperience;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.CvLanguage;
import com.jobwarden.cv.exception.CvErrorCode;
import com.jobwarden.cv.service.CvService;
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
			.contains("CV Robert.pdf", "83 KB", "Claude can read 4,512 characters of it.", "href=\"/cv/file\"", "Replace");
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
