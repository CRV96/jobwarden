package com.jobwarden.web.controller;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jobwarden.discover.service.DiscoveryService;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.inbox.dto.MailAccount;
import com.jobwarden.inbox.enums.InboxAutomation;
import com.jobwarden.inbox.service.InboxService;
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
import com.jobwarden.web.assistant.AssistantConnections;
import com.jobwarden.web.assistant.AssistantConnections.Connection;
import com.jobwarden.web.assistant.AssistantConnections.State;
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
	private AssistantConnections connections;

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

	@MockitoBean
	private InboxService inbox;

	@BeforeEach
	void noAdzunaKey() {
		given(this.adzunaKeys.find()).willReturn(AdzunaKey.NONE);
	}

	@Test
	void saysWhichAssistantsAreConnectedAlready() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.connections.find(Assistant.CLAUDE_CODE))
			.willReturn(new Connection(State.CONNECTED, Path.of("/Users/robert/Developer/jobwarden")));
		given(this.connections.find(Assistant.CODEX)).willReturn(new Connection(State.NOT_CONNECTED, null));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("Connected", "Not connected yet",
					"Connected in /Users/robert/Developer/jobwarden alone: Open in Claude starts its session there.");
	}

	@Test
	void saysWhenAnAssistantIsSetUpForAnotherProfile() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		// Claude Code with this profile's id from before; Codex with another profile's
		given(this.connections.find(Assistant.CLAUDE_CODE)).willReturn(new Connection(State.CONNECTED, null, "1000"));
		given(this.connections.find(Assistant.CODEX))
			.willReturn(new Connection(State.CONNECTED, null, "9d2e4b1a-0000-4000-8000-000000000001"));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("Connected to another profile",
					"Its settings name another profile (9d2e4b1a-0000-4000-8000-000000000001). The requests from the pages still work",
					"replace the jobwarden lines in ~/.codex/config.toml")
			.doesNotContain("run claude mcp remove jobwarden");
	}

	@Test
	void tellsTheLicenseWhoMadeItAndWhereItsCodeIs() {
		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("About", "MIT License", "href=\"https://github.com/CRV96/jobwarden/blob/main/LICENSE.md\"",
					"Made by", ">CRV<", "href=\"https://github.com/CRV96\"", "href=\"https://github.com/CRV96/jobwarden\"",
					"github.com/CRV96/jobwarden", "as long as the copyright notice and the license stay with it");
	}

	@Test
	void offersToRenameTheProfileBesideItsPhoto() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("action=\"/profiles/rename\"", "name=\"name\"", "value=\"Robert\"", "Rename")
			// And to delete it, its name typed to confirm
			.contains("id=\"delete-profile\"", "Delete profile", "action=\"/profiles/delete\"", "Delete Robert?",
					"Type Robert to confirm", "Delete for good", "href=\"/applications/export\"");
	}

	@Test
	void showsWhatTheExtensionNeeds() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			// The profile's public id, to copy, in place of the number the database counts with
			.contains("http://localhost", ">" + ROBERT.publicId() + "<", "data-copy-text=\"" + ROBERT.publicId() + "\"",
					"data-theme-choice")
			.doesNotContain(">1000<");
	}

	@Test
	void showsHowToConnectClaudeCode() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains(">http://localhost/mcp<", "Connect Claude Code to JobWarden (MCP)",
					"claude mcp add --scope user --transport http jobwarden http://localhost/mcp --header &quot;X-JobWarden-Profile: "
							+ ROBERT.publicId() + "&quot;",
					// The profile picks its assistant; ChatGPT's app can't reach a server on this computer
					"name=\"assistant\" value=\"CODEX\"", "The ChatGPT app can’t connect")
			.containsPattern("value=\"CLAUDE_CODE\"[^>]*checked");
	}

	@Test
	void showsHowToConnectCodexToAProfileThatWorksWithIt() {
		given(this.currentUser.get()).willReturn(Optional.of(new User(1000, "Robert", Instant.parse("2026-09-28T10:00:00Z"),
				null, Assistant.CODEX)));

		assertThat(this.mvc.get().uri("/settings")).bodyText()
			.contains("Connect Codex to JobWarden (MCP)", "~/.codex/config.toml", "[mcp_servers.jobwarden]",
					"url = &quot;http://localhost/mcp&quot;",
					"http_headers = { &quot;X-JobWarden-Profile&quot; = &quot;" + ROBERT.publicId() + "&quot; }",
					// Both can be connected: the pages open either app
					"Connect Claude Code to JobWarden (MCP)", "claude mcp add --scope user", "Your main assistant")
			.containsPattern("value=\"CODEX\"[^>]*checked");
	}

	@Test
	void savesTheAssistantTheProfileWorksWith() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/settings/assistant").param("assistant", "CODEX")).hasStatus3xxRedirection()
			.hasRedirectedUrl("/settings#assistant")
			.flash()
			.containsEntry("assistantSaved", true);
		then(this.users).should().changeAssistant(1000, Assistant.CODEX);
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
	void choosesHowOftenTheInboxReadsAndWhatBecomesOfTheUpdates() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));
		given(this.inbox.findAccount(ROBERT.id())).willReturn(Optional.of(new MailAccount("127.0.0.1", 1143, "robert@proton.me",
				"INBOX", null, null, null, null, 30, InboxAutomation.MATCHED)));

		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("id=\"inbox\"", "robert@proton.me", "<option value=\"MINUTES_30\" selected=\"selected\">every 30 minutes",
					"only when I click Check now", "value=\"MATCHED\" checked=\"checked\"", "And let the AI Assistant sort the rest");
	}

	@Test
	void savesTheInboxSettings() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.post().uri("/settings/inbox").param("readEvery", "ON_REQUEST").param("automation", "CLAUDE"))
			.hasStatus3xxRedirection()
			.hasRedirectedUrl("/settings#inbox")
			.flash()
			.containsEntry("inboxSaved", true);
		then(this.inbox).should().changeSettings(ROBERT.id(), 0, InboxAutomation.CLAUDE);
	}

	@Test
	void saysWhereToConnectAMailboxFirst() {
		given(this.currentUser.get()).willReturn(Optional.of(ROBERT));

		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("No mailbox connected", "href=\"/inbox\"")
			.doesNotContain("/settings/inbox");
	}

	@Test
	void opensWithoutAProfile() {
		assertThat(this.mvc.get().uri("/settings")).hasStatusOk()
			.bodyText()
			.contains("Choose a profile first", "href=\"/profiles\"");
	}

}
