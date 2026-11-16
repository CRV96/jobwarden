package com.jobwarden.capture.controller;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.service.UserService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ProfileController.class)
class ProfileControllerTests {

	private static final UUID PUBLIC_ID = UUID.fromString("5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService users;

	@Test
	void findsTheProfileTheExtensionIsGivenByItsPublicId() {
		given(this.users.findByKey(PUBLIC_ID.toString())).willReturn(Optional.of(new User(7, "Robert",
				Instant.parse("2026-09-28T10:00:00Z"), null, Assistant.DEFAULT, PUBLIC_ID)));

		assertThat(this.mvc.get()
			.uri("/api/captures/profile")
			.param("key", PUBLIC_ID.toString())
			.header(HttpHeaders.ORIGIN, "chrome-extension://abcdefghijklmnopabcdefghijklmnop"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 7, "publicId": "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41", "name": "Robert"}""");
	}

	@Test
	void saysWhenTheIdNamesNoProfile() {
		assertThat(this.mvc.get().uri("/api/captures/profile").param("key", "nobody")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
