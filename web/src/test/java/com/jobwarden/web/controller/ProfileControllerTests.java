package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.web.user.CurrentUser;
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

@WebMvcTest(ProfileController.class)
class ProfileControllerTests {

	private static final User ROBERT = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private CurrentUser currentUser;

	// For the sidebar, which every page controller fills in
	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@Test
	void listsTheProfiles() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));

		assertThat(this.mvc.get().uri("/profiles")).hasStatusOk().hasViewName("profiles/list").bodyText().contains("Robert");
	}

	@Test
	void createsTheProfileAndSelectsIt() {
		given(this.users.create("Robert")).willReturn(ROBERT);

		assertThat(this.mvc.post().uri("/profiles").param("name", "Robert")).hasRedirectedUrl("/applications");
		then(this.currentUser).should().select(1);
	}

	@Test
	void showsATakenNameAsAFormError() {
		given(this.users.create("Robert")).willThrow(
				new BusinessRuleException(IdentityErrorCode.USER_NAME_TAKEN, "A profile with this name already exists",
						"Robert"));

		assertThat(this.mvc.post().uri("/profiles").param("name", "Robert")).hasStatusOk()
			.bodyText()
			.contains("A profile named Robert already exists.");
		then(this.currentUser).should(never()).select(anyLong());
	}

	@Test
	void rejectsABlankName() {
		assertThat(this.mvc.post().uri("/profiles").param("name", "  ")).hasStatusOk()
			.bodyText()
			.contains("A profile needs a name");
		then(this.users).should(never()).create(any());
	}

	@Test
	void showsEachProfilesIdForTheExtension() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/profiles")).hasStatusOk().bodyText().contains("Profile id 1");
	}

	@Test
	void selectsAProfile() {
		assertThat(this.mvc.post().uri("/profiles/1/select")).hasRedirectedUrl("/applications");
		then(this.currentUser).should().select(1);
	}

}
