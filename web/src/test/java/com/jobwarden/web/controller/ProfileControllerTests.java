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
import org.springframework.mock.web.MockMultipartFile;
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
	void servesAProfilesPhotoForTheBrowserToKeep() {
		byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
		given(this.users.findImage(1)).willReturn(Optional.of(png));

		assertThat(this.mvc.get().uri("/profiles/1/image?v=1")).hasStatusOk()
			.hasContentType("image/png")
			.hasHeader("Cache-Control", "max-age=31536000, private")
			.body()
			.isEqualTo(png);
		assertThat(this.mvc.get().uri("/profiles/2/image")).hasStatus(404);
	}

	@Test
	void showsThePhotoInPlaceOfTheInitials() {
		User withPhoto = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"), Instant.ofEpochMilli(1234));
		given(this.users.findAll()).willReturn(List.of(withPhoto, new User(2, "Ana Pop", Instant.parse("2026-09-28T10:00:00Z"))));

		assertThat(this.mvc.get().uri("/profiles")).hasStatusOk()
			.bodyText()
			.contains("src=\"/profiles/1/image?v=1234\"", ">AP</span>")
			.doesNotContain(">R</span>");
	}

	@Test
	void savesTheCurrentProfilesPhotoAndSaysSoOnTheSettingsPage() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		MockMultipartFile photo = new MockMultipartFile("image", "me.jpg", "image/jpeg", new byte[] { 1, 2, 3 });

		assertThat(this.mvc.post().multipart().uri("/profiles/image").file(photo)).hasRedirectedUrl("/settings#profile")
			.flash()
			.containsEntry("profileImageSaved", true);
		then(this.users).should().changeImage(1, new byte[] { 1, 2, 3 });
	}

	@Test
	void saysWhyAPhotoWasntSaved() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.users.changeImage(1, new byte[] { 1 })).willThrow(
				new BusinessRuleException(IdentityErrorCode.IMAGE_UNREADABLE, "Not an image"));
		MockMultipartFile notAPhoto = new MockMultipartFile("image", "notes.txt", "text/plain", new byte[] { 1 });

		assertThat(this.mvc.post().multipart().uri("/profiles/image").file(notAPhoto)).hasRedirectedUrl("/settings#profile")
			.flash()
			.containsEntry("profileImageError", "That file isn’t a photo JobWarden can read: use a PNG, JPEG or GIF.");
	}

	@Test
	void removesTheCurrentProfilesPhoto() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/profiles/image/remove")).hasRedirectedUrl("/settings#profile")
			.flash()
			.containsEntry("profileImageRemoved", true);
		then(this.users).should().removeImage(1);
	}

	@Test
	void renamesTheCurrentProfileAndSaysSoOnTheSettingsPage() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/profiles/rename").param("name", "Rob")).hasRedirectedUrl("/settings#profile")
			.flash()
			.containsEntry("profileNameSaved", true);
		then(this.users).should().rename(1, "Rob");
	}

	@Test
	void keepsATakenOrBlankNameAsTypedWithWhy() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.users.rename(1, "Ana")).willThrow(new BusinessRuleException(IdentityErrorCode.USER_NAME_TAKEN,
				"A profile with this name already exists", "Ana"));

		assertThat(this.mvc.post().uri("/profiles/rename").param("name", "Ana")).hasRedirectedUrl("/settings#profile")
			.flash()
			.containsEntry("profileNameError", "A profile named Ana already exists.")
			.containsEntry("profileNameTyped", "Ana");
		assertThat(this.mvc.post().uri("/profiles/rename").param("name", " ")).flash()
			.containsEntry("profileNameError", "A profile needs a name.");
		then(this.users).should(never()).rename(1, " ");
	}

	@Test
	void deletesTheCurrentProfileOnceItsNameIsTypedAndSaysSo() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/profiles/delete").param("confirm", " Robert ")).hasRedirectedUrl("/profiles")
			.flash()
			.containsEntry("profileDeleted", "Robert");
		then(this.users).should().delete(1);
		then(this.currentUser).should().forget();
	}

	@Test
	void deletesNothingWhenTheNameTypedIsAnother() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/profiles/delete").param("confirm", "robert")).hasRedirectedUrl("/settings#delete-profile")
			.flash()
			.containsEntry("profileDeleteError", true);
		assertThat(this.mvc.post().uri("/profiles/delete")).hasRedirectedUrl("/settings#delete-profile");
		then(this.users).should(never()).delete(anyLong());
		then(this.currentUser).should(never()).forget();
	}

	@Test
	void saysOnTheProfilesPageThatOneWasDeleted() {
		given(this.users.findAll()).willReturn(List.of());

		assertThat(this.mvc.get().uri("/profiles").flashAttr("profileDeleted", "Robert")).hasStatusOk()
			.bodyText()
			.contains("Deleted Robert and everything of it.");
	}

	@Test
	void listsTheProfiles() {
		given(this.users.findAll()).willReturn(List.of(ROBERT));

		assertThat(this.mvc.get().uri("/profiles")).hasStatusOk().hasViewName("profiles/list").bodyText().contains("Robert");
	}

	@Test
	void createsTheProfileAndSelectsIt() {
		given(this.users.create("Robert")).willReturn(ROBERT);

		assertThat(this.mvc.post().uri("/profiles").param("name", "Robert")).hasRedirectedUrl("/");
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

		assertThat(this.mvc.get().uri("/profiles")).hasStatusOk().bodyText().contains("Profile ID " + ROBERT.publicId());
	}

	@Test
	void selectsAProfile() {
		assertThat(this.mvc.post().uri("/profiles/1/select")).hasRedirectedUrl("/");
		then(this.currentUser).should().select(1);
	}

}
