package com.jobwarden;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JobWardenApplicationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void contextLoads() {
	}

	/**
	 * The pages' scripts are asked for again on every use, by their content's ETag: after an upgrade, the browser gets
	 * the new ones, though the build dates every version's files the same.
	 */
	@Test
	void servesThePagesFilesToBeRevalidatedByTheirContent() {
		MvcTestResult script = this.mvc.get().uri("/js/app.js").exchange();

		assertThat(script).hasStatusOk()
			.hasHeader("Cache-Control", "no-cache")
			.doesNotContainHeader("Last-Modified")
			.containsHeader("ETag");
		String etag = script.getResponse().getHeader("ETag");
		assertThat(this.mvc.get().uri("/js/app.js").header("If-None-Match", etag)).hasStatus(304);
		assertThat(this.mvc.get().uri("/js/app/pipeline.js").header("If-None-Match", etag)).as("another file")
			.hasStatusOk();
	}

}
