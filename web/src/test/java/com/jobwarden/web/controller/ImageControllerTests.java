package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.Optional;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ImageController.class)
class ImageControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private CurrentUser currentUser;

	@Test
	void servesTheProfilesIconForTheSite() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findSourceIcon(1, "linkedin.com")).willReturn(Optional.of(new PngImage(PNG)));

		assertThat(this.mvc.get().uri("/sources/linkedin.com/icon")).hasStatusOk()
			.hasContentType(MediaType.IMAGE_PNG)
			.satisfies(result -> assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=86400, private"))
			.body()
			.isEqualTo(PNG);
	}

	@Test
	void servesTheLogoOfTheProfilesApplication() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.applications.findCompanyLogo(1, 7)).willReturn(Optional.of(new PngImage(PNG)));

		assertThat(this.mvc.get().uri("/applications/7/logo")).hasStatusOk()
			.hasContentType(MediaType.IMAGE_PNG)
			.body()
			.isEqualTo(PNG);
		assertThat(this.mvc.get().uri("/applications/8/logo")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void answers404WithoutAnIcon() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/sources/example.com/icon")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void answers404WithoutAProfile() {
		assertThat(this.mvc.get().uri("/sources/linkedin.com/icon")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
