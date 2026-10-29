package com.jobwarden.capture.controller;

import java.net.URI;
import java.time.Instant;

import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(VersionController.class)
class VersionControllerTests {

	private static final URI RELEASES = URI.create("https://github.com/CRV96/jobwarden/releases");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UpdateService updates;

	@Test
	void saysTheServersVersionAndTheLatestRelease() {
		given(this.updates.status()).willReturn(new UpdateStatus("0.1.0", new Release("0.2.0", "JobWarden 0.2.0",
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v0.2.0"), Instant.parse("2026-10-27T10:00:00Z")),
				RELEASES));

		assertThat(this.mvc.get().uri("/api/captures/version").header(HttpHeaders.ORIGIN, "moz-extension://1234"))
			.hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "moz-extension://1234")
			.bodyJson()
			.isLenientlyEqualTo("""
					{"version": "0.1.0",
					 "latest": {"version": "0.2.0", "name": "JobWarden 0.2.0",
					            "url": "https://github.com/CRV96/jobwarden/releases/tag/v0.2.0",
					            "publishedAt": "2026-10-27T10:00:00Z"},
					 "releases": "https://github.com/CRV96/jobwarden/releases"}""");
	}

	@Test
	void hasNoLatestReleaseBeforeThereIsOne() {
		given(this.updates.status()).willReturn(new UpdateStatus("0.1.0", null, RELEASES));

		assertThat(this.mvc.get().uri("/api/captures/version")).bodyJson()
			.isLenientlyEqualTo("{\"version\": \"0.1.0\", \"latest\": null}");
	}

}
