package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvFile;
import com.jobwarden.cv.dto.CvFileContent;
import com.jobwarden.cv.dto.TailoredCv;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.jobs.dto.ApplicationDetails;
import com.jobwarden.jobs.enums.ApplicationStatus;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.view.CvPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * With the real PDF renderer. No session: Claude downloads it.
 */
@WebMvcTest(ClaudeFilesController.class)
@Import(CvPdf.class)
class ClaudeFilesControllerTests {

	private static final CvDetails DETAILS = CvDetails.builder()
		.fullName("Robert Ciapă")
		.headline("Backend Engineer")
		.experience(List.of())
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private CvService cvs;

	@BeforeEach
	void aJob() {
		given(this.applications.findById(1, 7)).willReturn(Optional.of(ApplicationDetails.builder()
			.id(7)
			.title("Backend engineer")
			.company("Acme")
			.status(ApplicationStatus.SAVED)
			.capturedAt(Instant.now())
			.timeline(List.of())
			.build()));
		given(this.cvs.find(1)).willReturn(new Cv(null, DETAILS));
	}

	@Test
	void givesTheCvTailoredToTheJob() {
		given(this.cvs.findTailored(1, 7)).willReturn(Optional.of(TailoredCv.builder()
			.applicationId(7)
			.details(DETAILS)
			.updatedAt(Instant.now())
			.build()));

		assertThat(this.mvc.get().uri("/claude/cv?profile=1&job=7")).hasStatusOk()
			.hasContentType("application/pdf")
			.satisfies(result -> assertThat(result.getResponse().getContentAsByteArray())
				.startsWith("%PDF".getBytes(StandardCharsets.US_ASCII)))
			.headers()
			.hasValue("Content-Disposition",
					"attachment; filename=\"CV Robert Ciapa - Acme.pdf\"; filename*=UTF-8''CV%20Robert%20Ciap%C4%83%20-%20Acme.pdf");
	}

	@Test
	void givesTheCvBuiltFromTheFieldsOrElseTheFileUploaded() {
		assertThat(this.mvc.get().uri("/claude/cv?profile=1&job=7")).hasStatusOk()
			.hasContentType("application/pdf")
			.headers()
			.hasValue("Content-Disposition", "attachment; filename=\"CV Robert Ciapa.pdf\"; filename*=UTF-8''CV%20Robert%20Ciap%C4%83.pdf");

		given(this.cvs.find(1)).willReturn(new Cv(new CvFile("cv.pdf", "application/pdf", 3, 0, Instant.now()), CvDetails.NONE));
		given(this.cvs.downloadFile(1)).willReturn(new CvFileContent("cv.pdf", "application/pdf", new byte[] { 37, 80, 68 }));
		assertThat(this.mvc.get().uri("/claude/cv?profile=1&job=7")).hasStatusOk().hasBodyTextEqualTo("%PD");
	}

	@Test
	void hasNothingForAnotherProfilesJobOrWithoutACv() {
		assertThat(this.mvc.get().uri("/claude/cv?profile=2&job=7")).hasStatus(HttpStatus.NOT_FOUND);

		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));
		assertThat(this.mvc.get().uri("/claude/cv?profile=1&job=7")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
