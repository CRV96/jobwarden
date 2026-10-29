package com.jobwarden.web.controller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.jobs.dto.CsvImport;
import com.jobwarden.jobs.dto.CsvImportProblem;
import com.jobwarden.jobs.enums.CsvProblem;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.market.dto.AdzunaKey;
import com.jobwarden.market.enums.AdzunaKeyCheck;
import com.jobwarden.market.enums.AdzunaKeySource;
import com.jobwarden.market.service.AdzunaKeyService;
import com.jobwarden.updates.dto.Release;
import com.jobwarden.updates.dto.UpdateStatus;
import com.jobwarden.updates.service.UpdateService;
import com.jobwarden.web.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * The language picker is covered by LocalizationTests.
 */
@WebMvcTest(SettingsController.class)
class SettingsControllerTests {

	private static final User ROBERT = new User(1000, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

	private static final URI RELEASES = URI.create("https://github.com/CRV96/jobwarden/releases");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CurrentUser currentUser;

	@MockitoBean
	private UserService users;

	@MockitoBean
	private ApplicationService applications;

	@MockitoBean
	private DiscoveryService discovery;

	@MockitoBean
	private UpdateService updates;

	@MockitoBean
	private AdzunaKeyService adzunaKeys;

	@BeforeEach
	void noAdzunaKey() {
		given(this.adzunaKeys.find()).willReturn(AdzunaKey.NONE);
	}

	@Test
	void showsWhatTheExtensionNeeds() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("http://localhost", ">1000<", "data-theme-choice");
	}

	@Test
	void showsHowToConnectClaude() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains(">http://localhost/mcp<",
					"claude mcp add --transport http jobwarden http://localhost/mcp --header &quot;X-JobWarden-Profile: 1000&quot;");
	}

	@Test
	void saysWhenANewerJobWardenIsOutHereAndInTheSidebar() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.updates.status()).willReturn(new UpdateStatus("0.1.0", new Release("0.2.0", "JobWarden 0.2.0",
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v0.2.0"), null), RELEASES));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("JobWarden 0.1.0", "JobWarden 0.2.0 is out on GitHub.", "JobWarden 0.2.0 is out",
					"data-dismissible=\"update-0.2.0\"", "href=\"https://github.com/CRV96/jobwarden/releases/tag/v0.2.0\"");
	}

	@Test
	void saysWhenItIsUpToDate() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.updates.status()).willReturn(new UpdateStatus("0.2.0", new Release("0.2.0", null,
				URI.create("https://github.com/CRV96/jobwarden/releases/tag/v0.2.0"), null), RELEASES));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("JobWarden 0.2.0", "Up to date", "href=\"https://github.com/CRV96/jobwarden/releases\"")
			.doesNotContain("data-dismissible");
	}

	@Test
	void exportsAndImportsTheJobs() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("Your jobs", "href=\"/applications/export\"", "action=\"/applications/import\"",
					"enctype=\"multipart/form-data\"", "name=\"file\"")
			.doesNotContain("Import done");
	}

	@Test
	void saysHowAnImportWent() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		List<CsvImportProblem> problems = IntStream.rangeClosed(2, 13)
			.mapToObj(row -> new CsvImportProblem(row, CsvProblem.NO_TITLE, null))
			.collect(Collectors.toCollection(ArrayList::new));
		problems.set(1, new CsvImportProblem(3, CsvProblem.UNKNOWN_STATUS, "Pending"));

		assertThat(this.mvc.get().uri("/settings").flashAttr("csvImport", new CsvImport(2, 1, problems))).bodyText()
			.contains("Import done: 2 added, 1 already there.", "Row 2: no title.", "Row 3: “Pending” isn’t a status",
					"Row 11: no title.", "And 2 more.")
			.doesNotContain("Row 12: no title.");
		assertThat(this.mvc.get().uri("/settings").flashAttr("csvImportError", "The file has no title column.")).bodyText()
			.contains("The file has no title column.");
	}

	@Test
	void showsTheAdzunaKeyInUseButNotItsSecret() {
		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("id=\"salaries\"", "No key yet", "action=\"/settings/adzuna\"", "name=\"appKey\"",
					"type=\"password\"", "href=\"https://developer.adzuna.com/signup\"")
			.doesNotContain("Remove the saved key");

		given(this.adzunaKeys.find())
			.willReturn(new AdzunaKey(AdzunaKeySource.SETTINGS, "1a2b3c4d", "5f6e", Instant.now()));
		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains(">1a2b3c4d<", ">••••5f6e<", "Saved here", "action=\"/settings/adzuna/remove\"", ">Replace<");

		given(this.adzunaKeys.find()).willReturn(new AdzunaKey(AdzunaKeySource.ENVIRONMENT, "env", "0042", null));
		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("From the server’s ADZUNA_APP_ID and ADZUNA_APP_KEY")
			.doesNotContain("Remove the saved key");
	}

	@Test
	void savesAnAdzunaKeyAndSaysHowItWent() {
		given(this.adzunaKeys.save("1a2b3c4d", "0123456789abcdef")).willReturn(AdzunaKeyCheck.ACCEPTED);

		assertThat(this.mvc.post().uri("/settings/adzuna").param("appId", "1a2b3c4d").param("appKey", "0123456789abcdef"))
			.hasRedirectedUrl("/settings#salaries")
			.flash()
			.containsEntry("adzunaCheck", AdzunaKeyCheck.ACCEPTED)
			.doesNotContainKey("adzunaAppId");
		assertThat(this.mvc.get().uri("/settings").flashAttr("adzunaCheck", AdzunaKeyCheck.ACCEPTED)).bodyText()
			.contains("Adzuna accepted the key");
		assertThat(this.mvc.get().uri("/settings").flashAttr("adzunaCheck", AdzunaKeyCheck.UNCHECKED)).bodyText()
			.contains("Saved, but Adzuna couldn’t be reached");
	}

	@Test
	void givesBackTheAppIdButNotTheKeyAdzunaRefused() {
		// The service drops the spaces of a copy and paste
		given(this.adzunaKeys.save(" 1a2b3c4d ", "made-up")).willReturn(AdzunaKeyCheck.REFUSED);

		assertThat(this.mvc.post().uri("/settings/adzuna").param("appId", " 1a2b3c4d ").param("appKey", "made-up"))
			.hasRedirectedUrl("/settings#salaries")
			.flash()
			.containsEntry("adzunaCheck", AdzunaKeyCheck.REFUSED)
			.containsEntry("adzunaAppId", "1a2b3c4d")
			.doesNotContainValue("made-up");
		assertThat(this.mvc.get()
			.uri("/settings")
			.flashAttr("adzunaCheck", AdzunaKeyCheck.REFUSED)
			.flashAttr("adzunaAppId", "1a2b3c4d")).bodyText()
			.contains("Adzuna didn’t accept this app ID and app key", "value=\"1a2b3c4d\"");
	}

	@Test
	void checksTheKeyBeforeAskingAdzuna() {
		assertThat(this.mvc.post().uri("/settings/adzuna").param("appId", "1a2b3c4d").param("appKey", " "))
			.hasRedirectedUrl("/settings#salaries")
			.flash()
			.containsEntry("adzunaError", "Enter the app key.");
		assertThat(this.mvc.post().uri("/settings/adzuna").param("appId", "1a 2b").param("appKey", "<script>"))
			.flash()
			.containsEntry("adzunaError", "The app ID and the app key are letters and digits, as Adzuna shows them.");
		then(this.adzunaKeys).should(never()).save(any(), any());
	}

	@Test
	void removesTheSavedAdzunaKey() {
		assertThat(this.mvc.post().uri("/settings/adzuna/remove"))
			.hasRedirectedUrl("/settings#salaries")
			.flash()
			.containsEntry("adzunaRemoved", true);
		then(this.adzunaKeys).should().remove();
	}

	@Test
	void opensWithoutAProfile() {
		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("Choose a profile first", "href=\"/profiles\"");
	}

}
