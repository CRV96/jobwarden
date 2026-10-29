package com.jobwarden.capture.controller;

import java.util.List;

import com.jobwarden.apply.dto.ApplicationAnswers;
import com.jobwarden.apply.dto.CustomAnswer;
import com.jobwarden.apply.service.AutoApplyService;
import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.identity.service.UserService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(AnswersController.class)
class AnswersControllerTests {

	private static final String FIREFOX_EXTENSION = "moz-extension://11111111-2222-3333-4444-555555555555";

	private static final String CHROME_EXTENSION = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

	private static final ApplicationAnswers ANSWERS = ApplicationAnswers.builder()
		.needsSponsorship(false)
		.noticePeriod("1 month")
		.yearsOfExperience(8)
		.declineVoluntary(true)
		.custom(List.of(new CustomAnswer("Driving licence?", "Yes, B")))
		.build();

	@Autowired
	private MockMvcTester mvc;

	@Value("${jobwarden.capture.path}")
	private String capturePath;

	@MockitoBean
	private AutoApplyService autoApply;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private UserService users;

	@Test
	void givesTheContactDetailsAndTheAnswers() {
		given(this.users.exists(1)).willReturn(true);
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder()
			.fullName("Robert Ciapa")
			.email("robert@example.com")
			.linkedin("https://www.linkedin.com/in/robert")
			.build()));
		given(this.autoApply.findAnswers(1)).willReturn(ANSWERS);

		assertThat(this.mvc.get().uri(this.capturePath + "/answers").param("userId", "1"))
			.hasStatusOk()
			.bodyJson()
			.satisfies(body -> {
				assertThat(body).extractingPath("$.contact.fullName").isEqualTo("Robert Ciapa");
				assertThat(body).extractingPath("$.contact.linkedin").isEqualTo("https://www.linkedin.com/in/robert");
				assertThat(body).extractingPath("$.needsSponsorship").isEqualTo(false);
				assertThat(body).extractingPath("$.yearsOfExperience").isEqualTo(8);
				assertThat(body).extractingPath("$.declineVoluntary").isEqualTo(true);
				assertThat(body).extractingPath("$.custom[0].question").isEqualTo("Driving licence?");
				assertThat(body).extractingPath("$.custom[0].answer").isEqualTo("Yes, B");
			});
	}

	@Test
	void keepsWhatTheUserAnsweredDuringARun() {
		given(this.users.exists(1)).willReturn(true);
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));
		given(this.autoApply.learn(1, "Are you willing to work weekends?", "Now and then")).willReturn(ANSWERS);

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/answers")
			.param("userId", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "question": "Are you willing to work weekends?", "answer": "Now and then" }
					"""))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.noticePeriod")
			.isEqualTo("1 month");
		then(this.autoApply).should().learn(1, "Are you willing to work weekends?", "Now and then");
	}

	@Test
	void rejectsAnAnswerWithoutItsQuestion() {
		given(this.users.exists(1)).willReturn(true);

		assertThat(this.mvc.post()
			.uri(this.capturePath + "/answers")
			.param("userId", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "question": " ", "answer": "Now and then" }
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST);
		then(this.autoApply).should(never()).learn(anyLong(), any(), any());
	}

	@Test
	void rejectsAnUnknownProfile() {
		given(this.users.exists(1)).willReturn(false);

		assertThat(this.mvc.get().uri(this.capturePath + "/answers").param("userId", "1"))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.errorCode")
			.isEqualTo("IDENTITY-001");
	}

	@Test
	void takesAnAnswerFromChromesExtension() {
		given(this.users.exists(1)).willReturn(true);
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.NONE));
		given(this.autoApply.learn(1, "Years with Python?", "2")).willReturn(ANSWERS);

		// Chrome sends its extension's origin with a POST
		assertThat(this.mvc.post()
			.uri(this.capturePath + "/answers")
			.param("userId", "1")
			.header(HttpHeaders.ORIGIN, CHROME_EXTENSION)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "question": "Years with Python?", "answer": "2" }
					"""))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CHROME_EXTENSION);
	}

	@Test
	void turnsAwayWebPages() {
		assertThat(this.mvc.post()
			.uri(this.capturePath + "/answers")
			.param("userId", "1")
			.header(HttpHeaders.ORIGIN, "https://example.com")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "question": "Years with Python?", "answer": "2" }
					"""))
			.hasStatus(HttpStatus.FORBIDDEN);
		then(this.autoApply).should(never()).learn(anyLong(), any(), any());
	}

	@Test
	void letsFirefoxExtensionsSendAnswers() {
		assertThat(this.mvc.options()
			.uri(this.capturePath + "/answers")
			.header(HttpHeaders.ORIGIN, FIREFOX_EXTENSION)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FIREFOX_EXTENSION);
	}

}
