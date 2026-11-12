package com.jobwarden.web.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.web.assistant.AssistantConnections.Connection;
import com.jobwarden.web.assistant.AssistantConnections.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads configurations written as Claude Code and Codex write them, in a home of its own.
 */
class AssistantConnectionsTests {

	@TempDir
	private Path home;

	@Test
	void findsJobWardenInClaudeCodesConfigurationForEveryFolderOrForSome() throws IOException {
		AssistantConnections connections = connections();
		Path project = Files.createDirectories(this.home.resolve("jobs"));

		claude("""
				{"mcpServers": {"jobwarden": {"type": "http", "url": "http://localhost:8080/mcp"}}}""");
		assertThat(connections.find(Assistant.CLAUDE_CODE)).isEqualTo(new Connection(State.CONNECTED, null));

		// With the profile it works on when a tool isn't told one
		claude("""
				{"mcpServers": {"jobwarden": {"type": "http", "url": "http://localhost:8080/mcp",
				                              "headers": {"X-JobWarden-Profile": "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41"}}}}""");
		assertThat(connections.find(Assistant.CLAUDE_CODE))
			.isEqualTo(new Connection(State.CONNECTED, null, "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41"));

		// Under another name, at this computer's /mcp
		claude("""
				{"mcpServers": {"jw": {"type": "http", "url": "http://127.0.0.1:8081/mcp/"}}}""");
		assertThat(connections.find(Assistant.CLAUDE_CODE).state()).isEqualTo(State.CONNECTED);

		// In one folder alone (claude mcp add's local scope): the session starts there
		claude("""
				{"mcpServers": {"github": {"type": "http", "url": "https://api.githubcopilot.com/mcp/"}},
				 "projects": {"%s": {"mcpServers": {"jobwarden": {"type": "http", "url": "http://localhost:8080/mcp",
				                                                  "headers": {"X-JobWarden-Profile": "1"}}}},
				              "/no/longer/here": {"mcpServers": {"jobwarden": {}}}}}""".formatted(project));
		assertThat(connections.find(Assistant.CLAUDE_CODE)).isEqualTo(new Connection(State.CONNECTED, project, "1"));

		claude("""
				{"mcpServers": {"github": {"type": "http", "url": "https://api.githubcopilot.com/mcp/"}}}""");
		assertThat(connections.find(Assistant.CLAUDE_CODE).isMissing()).isTrue();
	}

	@Test
	void tellsWhetherItsSetUpForAProfileByEitherOfItsIds() {
		User robert = new User(1, "Robert", Instant.parse("2026-09-28T10:00:00Z"));

		assertThat(new Connection(State.CONNECTED, null, robert.publicId().toString().toUpperCase()).isFor(robert)).isTrue();
		assertThat(new Connection(State.CONNECTED, null, "1").isFor(robert)).isTrue();
		assertThat(new Connection(State.CONNECTED, null, "2").isFor(robert)).isFalse();
		assertThat(new Connection(State.CONNECTED, null).isFor(robert)).isFalse();
	}

	@Test
	void saysNothingWhenItCantTell() throws IOException {
		// No Claude Code here at all, as in Docker
		assertThat(connections().find(Assistant.CLAUDE_CODE)).isEqualTo(Connection.UNKNOWN);
		assertThat(connections().find(Assistant.CODEX)).isEqualTo(Connection.UNKNOWN);
		// Claude Code, never told of a server
		Files.createDirectories(this.home.resolve(".claude"));
		assertThat(connections().find(Assistant.CLAUDE_CODE).isMissing()).isTrue();
		// A configuration that isn't JSON
		claude("{ not json");
		assertThat(connections().find(Assistant.CLAUDE_CODE)).isEqualTo(Connection.UNKNOWN);
	}

	@Test
	void findsJobWardenInCodexsConfigurationUnlessTurnedOff() throws IOException {
		AssistantConnections connections = connections();

		codex("""
				model = "gpt-5.5"

				[mcp_servers.node_repl]
				command = "node"

				[mcp_servers.node_repl.env]
				url = "http://localhost:8080/mcp"
				""");
		assertThat(connections.find(Assistant.CODEX).isMissing()).isTrue();

		codex("""
				[mcp_servers.jobwarden]
				url = "http://localhost:8080/mcp"
				http_headers = { "X-JobWarden-Profile" = "1" }
				""");
		assertThat(connections.find(Assistant.CODEX)).isEqualTo(new Connection(State.CONNECTED, null, "1"));

		// The headers in a table of their own
		codex("""
				[mcp_servers.jobwarden]
				url = "http://localhost:8080/mcp"

				[mcp_servers.jobwarden.http_headers]
				"X-JobWarden-Profile" = "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41"
				""");
		assertThat(connections.find(Assistant.CODEX))
			.isEqualTo(new Connection(State.CONNECTED, null, "5b1f3c2e-8a4d-4c6e-9f10-2d7b8e3a6c41"));

		codex("""
				[mcp_servers."jobwarden"]
				url = "http://localhost:8080/mcp"
				enabled = false
				""");
		assertThat(connections.find(Assistant.CODEX).isMissing()).isTrue();

		// Dotted, under another name
		codex("""
				mcp_servers.jw.url = "http://127.0.0.1:8080/mcp" # this computer

				[features]
				web_search = true
				""");
		assertThat(connections.find(Assistant.CODEX).state()).isEqualTo(State.CONNECTED);
	}

	private AssistantConnections connections() {
		return new AssistantConnections(this.home.resolve(".claude.json"), this.home.resolve(".codex/config.toml"), "/mcp");
	}

	private void claude(String json) throws IOException {
		write(this.home.resolve(".claude.json"), json);
	}

	private void codex(String toml) throws IOException {
		write(this.home.resolve(".codex/config.toml"), toml);
	}

	/** Each time as changed later, so the configuration is read again, however quick the test. */
	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		FileTime before = Files.exists(file) ? Files.getLastModifiedTime(file) : FileTime.from(Instant.EPOCH);
		Files.writeString(file, content);
		Files.setLastModifiedTime(file, FileTime.from(before.toInstant().plusSeconds(10).isAfter(Instant.now())
				? before.toInstant().plusSeconds(10) : Instant.now()));
	}

}
