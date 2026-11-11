package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.dto.Cv;
import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.Story;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.StoryService;
import com.jobwarden.discover.dto.AiRequirements;
import com.jobwarden.discover.enums.AiRequestKind;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(StoryController.class)
class StoryControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final Story KAFKA = Story.builder()
		.id(3)
		.title("The Kafka migration")
		.situation("Orders were lost at peaks.")
		.task("Stop losing them.")
		.action("Moved the queue to Kafka.")
		.result("[How many orders a day?]")
		.skills(List.of("Kafka"))
		.build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private StoryService stories;

	@MockitoBean
	private CvService cvs;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@BeforeEach
	void selectRobert() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.cvs.find(1)).willReturn(new Cv(null, CvDetails.builder().fullName("Robert Ciapă").build()));
	}

	@Test
	void showsTheStoriesWithWhatsLeftToFinish() {
		given(this.stories.findAll(1)).willReturn(List.of(KAFKA));

		assertThat(this.mvc.get().uri("/cv/stories").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("Story bank", "The Kafka migration", "Orders were lost at peaks.", "To finish", "1 of 40",
					"/cv/stories/3/edit", "Ask the AI Assistant to draft them from my CV");
	}

	@Test
	void writesANewStory() {
		assertThat(this.mvc.get().uri("/cv/stories/new").header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("name=\"situation\"", "name=\"result\"");

		assertThat(this.mvc.post()
			.uri("/cv/stories")
			.param("id", "0")
			.param("title", "Halving checkout latency")
			.param("action", "Cached the prices.")
			.param("skills", "performance, caching\nPostgreSQL")
			.header("HX-Request", "true")).hasStatusOk();
		then(this.stories).should().save(1, Story.builder()
			.title("Halving checkout latency")
			.action("Cached the prices.")
			.skills(List.of("performance", "caching", "PostgreSQL"))
			.build());
	}

	@Test
	void keepsTheFormOpenWhenAPartIsTooLong() {
		assertThat(this.mvc.post()
			.uri("/cv/stories")
			.param("id", "0")
			.param("title", "Halving checkout latency")
			.param("action", "x".repeat(2001))
			.header("HX-Request", "true")).hasStatusOk()
			.bodyText()
			.contains("at most 2000 characters", "value=\"Halving checkout latency\"");
		then(this.stories).should(never()).save(anyLong(), any());
	}

	@Test
	void asksClaudeToDraftStoriesFromTheCv() {
		this.mvc.post().uri("/cv/stories/request").header("HX-Request", "true").exchange();

		then(this.discovery).should().createRequest(1, AiRequestKind.WRITE_STORIES, AiRequirements.NONE);
	}

}
