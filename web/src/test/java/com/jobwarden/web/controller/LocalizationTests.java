package com.jobwarden.web.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.market.dto.AdzunaKey;
import com.jobwarden.market.service.AdzunaKeyService;
import com.jobwarden.web.enums.Language;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * The profiles page stands in for every page: they all get their language the same way. The settings page has the
 * language picker.
 */
@WebMvcTest({ ProfileController.class, SettingsController.class })
class LocalizationTests {

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

	@MockitoBean
	private AdzunaKeyService adzunaKeys;

	@BeforeEach
	void oneProfile() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.adzunaKeys.find()).willReturn(AdzunaKey.NONE);
	}

	@Test
	void showsThePageInTheBrowsersLanguage() {
		assertThat(this.mvc.get().uri("/profiles").header(HttpHeaders.ACCEPT_LANGUAGE, "ro-RO,ro;q=0.9,en;q=0.8"))
			.hasStatusOk()
			.bodyText()
			.contains("<html lang=\"ro\"", "Cine folosește JobWarden?", "Alege un profil ca să continui");
	}

	@Test
	void fallsBackToEnglishForALanguageTheAppDoesNotHave() {
		assertThat(this.mvc.get().uri("/profiles").header(HttpHeaders.ACCEPT_LANGUAGE, "it-IT"))
			.bodyText()
			.contains("<html lang=\"en\"", "Who’s using JobWarden?");
	}

	@Test
	void remembersTheLanguagePickedInTheHeader() {
		assertThat(this.mvc.get().uri("/profiles").param("lang", "de").header(HttpHeaders.ACCEPT_LANGUAGE, "ro"))
			.hasStatusOk()
			.cookies()
			.hasValue("language", "de");
		assertThat(this.mvc.get().uri("/profiles").param("lang", "de")).bodyText().contains("Wer nutzt JobWarden?");
	}

	@Test
	void ignoresALanguageTheAppDoesNotHave() {
		assertThat(this.mvc.get().uri("/profiles").param("lang", "it")).cookies().doesNotContainCookie("language");
	}

	@Test
	void offersEveryLanguageInThePicker() {
		String[] options = Arrays.stream(Language.values())
			.flatMap(language -> Stream.of("href=\"/settings?lang=" + language.getTag() + "\"", language.getNativeName(),
					"fi-" + language.getFlag()))
			.toArray(String[]::new);

		assertThat(this.mvc.get().uri("/settings")).bodyText().contains(options);
	}

	@Test
	void showsTheCurrentLanguageInThePicker() {
		assertThat(this.mvc.get().uri("/settings").param("lang", "pl")).bodyText()
			.contains("<html lang=\"pl\"", "Ustawienia", "href=\"/settings?lang=pl\" aria-checked=\"true\"");
	}

	@Test
	void translatesFormErrors() {
		assertThat(this.mvc.post().uri("/profiles").param("name", " ").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
			.bodyText()
			.contains("Un profil doit avoir un nom.");
	}

	@Test
	void translatesTheModulesErrors() {
		given(this.users.create("Robert")).willThrow(new BusinessRuleException(IdentityErrorCode.USER_NAME_TAKEN,
				"A profile with this name already exists", "Robert"));

		assertThat(this.mvc.post().uri("/profiles").param("name", "Robert").header(HttpHeaders.ACCEPT_LANGUAGE, "ro"))
			.bodyText()
			.contains("Există deja un profil cu numele Robert.");
	}

}
