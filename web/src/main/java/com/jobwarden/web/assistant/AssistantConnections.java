package com.jobwarden.web.assistant;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether each AI assistant has JobWarden's MCP server set up on this computer, from its own configuration: Claude
 * Code's ~/.claude.json (for every folder, or for some), Codex's ~/.codex/config.toml (which the Codex app reads too).
 * Without it, the assistant a page opens can't read the request, so the page says to connect it first.
 * <p>
 * A server counts when it's called jobwarden, or its address is this computer's /mcp. When the configuration can't
 * be read, or isn't on this computer at all (JobWarden in Docker, say), it says nothing either way. A file is read
 * again only once it changes.
 */
@Component
@CustomLog
public class AssistantConnections {

	/** The name the README and the Settings page give the server. */
	static final String SERVER_NAME = "jobwarden";

	/** The header that names the profile a client works on when a tool isn't told one (the mcp module's). */
	static final String PROFILE_HEADER = "X-JobWarden-Profile";

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

	/** A server's table in config.toml, e.g. [mcp_servers.jobwarden] or [mcp_servers."jobwarden"]. */
	private static final Pattern CODEX_SERVER = Pattern.compile("^\\[\\s*mcp_servers\\.\"?([^\".\\]]+)\"?\\s*]$");

	/** Any other table, e.g. [mcp_servers.jobwarden.env] or [features]. */
	private static final Pattern TOML_TABLE = Pattern.compile("^\\[.*]$");

	/** A server's headers as a table of their own, e.g. [mcp_servers.jobwarden.http_headers]. */
	private static final Pattern CODEX_HEADERS = Pattern.compile("^\\[\\s*mcp_servers\\.\"?([^\".\\]]+)\"?\\.http_headers\\s*]$");

	/** The profile header, inline (http_headers = { "X-JobWarden-Profile" = "1" }) or in the headers' table. */
	private static final Pattern CODEX_PROFILE = Pattern.compile("\"?" + Pattern.quote(PROFILE_HEADER) + "\"?\\s*=\\s*\"([^\"]*)\"");

	/** A key in a server's table, or dotted at the top: url = "...", mcp_servers.jobwarden.enabled = false. */
	private static final Pattern TOML_KEY = Pattern
		.compile("^(?:mcp_servers\\.\"?([^\".]+)\"?\\.)?(url|enabled)\\s*=\\s*(\"([^\"]*)\"|true|false)");

	private final Path claudeConfig;

	private final Path codexConfig;

	private final String mcpPath;

	private final JsonMapper json = JsonMapper.builder().build();

	private final Map<Assistant, Read> lastRead = new ConcurrentHashMap<>();

	/**
	 * @param claudeConfig Claude Code's configuration, which `claude mcp add` writes to
	 * @param codexConfig Codex's
	 * @param mcpPath the MCP server's path on this server, e.g. /mcp
	 */
	AssistantConnections(@Value("${jobwarden.assistant.claude-config:${user.home}/.claude.json}") Path claudeConfig,
			@Value("${jobwarden.assistant.codex-config:${user.home}/.codex/config.toml}") Path codexConfig,
			@Value("${jobwarden.mcp.path:/mcp}") String mcpPath) {
		this.claudeConfig = claudeConfig;
		this.codexConfig = codexConfig;
		this.mcpPath = mcpPath;
	}

	/**
	 * Whether the assistant has the server set up: for every folder, for some (Claude Code's local or project scope:
	 * then a folder it has it in), or not at all; or that it can't tell.
	 *
	 * @param state whether it's set up
	 * @param folder for Claude Code set up in some folders alone, one of them, for a session to start in
	 * @param profile the profile it was given, for when a tool isn't told one (its public id, or its id as given
	 * before); {@code null} for none
	 */
	public record Connection(State state, @Nullable Path folder, @Nullable String profile) {

		public static final Connection UNKNOWN = new Connection(State.UNKNOWN, null);

		static final Connection NOT_CONNECTED = new Connection(State.NOT_CONNECTED, null);

		public Connection(State state, @Nullable Path folder) {
			this(state, folder, null);
		}

		/**
		 * @return whether it's set up for this profile: the one it was given, whichever id names it
		 */
		public boolean isFor(User user) {
			return this.profile != null && (this.profile.equalsIgnoreCase(user.publicId().toString())
					|| this.profile.equals(Long.toString(user.id())));
		}

		/**
		 * @return whether it's known not to be set up
		 */
		public boolean isMissing() {
			return this.state == State.NOT_CONNECTED;
		}

	}

	public enum State {

		CONNECTED, NOT_CONNECTED, UNKNOWN

	}

	/** A configuration as read, kept until it changes. */
	private record Read(FileTime modified, long size, Connection connection) {
	}

	public Connection find(Assistant assistant) {
		Path file = (assistant == Assistant.CLAUDE_CODE) ? this.claudeConfig : this.codexConfig;
		try {
			if (!Files.isRegularFile(file)) {
				// Not set up, when the assistant is: its own directory is here, ~/.claude or ~/.codex
				Path home = (assistant == Assistant.CLAUDE_CODE) ? file.resolveSibling(".claude") : file.getParent();
				return (home != null && Files.isDirectory(home)) ? Connection.NOT_CONNECTED : Connection.UNKNOWN;
			}
			BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
			Read read = this.lastRead.get(assistant);
			if (read != null && read.modified().equals(attributes.lastModifiedTime()) && read.size() == attributes.size()) {
				return read.connection();
			}
			Connection connection = (assistant == Assistant.CLAUDE_CODE) ? fromClaude(file) : fromCodex(file);
			this.lastRead.put(assistant, new Read(attributes.lastModifiedTime(), attributes.size(), connection));
			log.debug("{}'s configuration {}: {}", assistant.product(), file, connection);
			return connection;
		} catch (IOException | RuntimeException unreadable) {
			log.debug("Couldn't read {}'s configuration {}: {}", assistant.product(), file, unreadable.getMessage());
			return Connection.UNKNOWN;
		}
	}

	/**
	 * ~/.claude.json: mcpServers at the top for every folder (user scope); under projects, a folder's own (local
	 * scope).
	 */
	private Connection fromClaude(Path file) {
		JsonNode config = this.json.readTree(file);
		Optional<JsonNode> everywhere = ours(config.path("mcpServers"));
		if (everywhere.isPresent()) {
			return new Connection(State.CONNECTED, null, profileOf(everywhere.get()));
		}
		return config.path("projects")
			.propertyStream()
			.filter(project -> Files.isDirectory(Path.of(project.getKey())))
			.sorted(Map.Entry.comparingByKey())
			.flatMap(project -> ours(project.getValue().path("mcpServers"))
				.map(server -> new Connection(State.CONNECTED, Path.of(project.getKey()), profileOf(server)))
				.stream())
			.findFirst()
			.orElse(Connection.NOT_CONNECTED);
	}

	private Optional<JsonNode> ours(JsonNode servers) {
		return servers.propertyStream()
			.filter(server -> isOurs(server.getKey(), server.getValue().path("url").stringValue(null)))
			.map(Map.Entry::getValue)
			.findFirst();
	}

	private static @Nullable String profileOf(JsonNode server) {
		String profile = server.path("headers").path(PROFILE_HEADER).stringValue(null);
		return (profile == null || profile.isBlank()) ? null : profile.strip();
	}

	/**
	 * config.toml, read as far as it needs: each [mcp_servers.NAME] table's url and enabled, or the same as dotted
	 * keys at the top.
	 */
	private Connection fromCodex(Path file) throws IOException {
		Map<String, @Nullable String> servers = new LinkedHashMap<>();
		Map<String, String> profiles = new HashMap<>();
		Set<String> disabled = new HashSet<>();
		String table = null;
		String headersOf = null;
		boolean top = true;
		for (String line : configuration(Files.readAllLines(file, StandardCharsets.UTF_8))) {
			String text = line.strip();
			if (text.isEmpty()) {
				continue;
			}
			Matcher server = CODEX_SERVER.matcher(text);
			if (server.matches()) {
				table = server.group(1).strip();
				headersOf = null;
				servers.putIfAbsent(table, null);
				top = false;
				continue;
			}
			Matcher headers = CODEX_HEADERS.matcher(text);
			if (headers.matches()) {
				table = null;
				headersOf = headers.group(1).strip();
				top = false;
				continue;
			}
			if (TOML_TABLE.matcher(text).matches()) {
				table = null;
				headersOf = null;
				top = false;
				continue;
			}
			Matcher profile = CODEX_PROFILE.matcher(text);
			String profiled = (headersOf != null) ? headersOf : table;
			if (profiled != null && profile.find() && !profile.group(1).isBlank()) {
				profiles.put(profiled, profile.group(1).strip());
				if (headersOf != null) {
					continue;
				}
			}
			Matcher key = TOML_KEY.matcher(text);
			if (!key.find()) {
				continue;
			}
			String name = (key.group(1) != null && top) ? key.group(1).strip() : (key.group(1) == null) ? table : null;
			if (name == null) {
				continue;
			}
			servers.putIfAbsent(name, null);
			if ("url".equals(key.group(2))) {
				servers.put(name, key.group(4));
			} else if ("false".equals(key.group(3))) {
				disabled.add(name);
			}
		}
		return servers.entrySet()
			.stream()
			.filter(server -> !disabled.contains(server.getKey()) && isOurs(server.getKey(), server.getValue()))
			.findFirst()
			.map(server -> new Connection(State.CONNECTED, null, profiles.get(server.getKey())))
			.orElse(Connection.NOT_CONNECTED);
	}

	/**
	 * The lines as TOML reads them: without comments, and without what's inside a multi-line string ("""...""" or
	 * '''...''', which may hold a table's name or a key as text), which a line of its own leaves blank. One on a
	 * single line reads as the string it is.
	 */
	static List<String> configuration(List<String> lines) {
		List<String> kept = new ArrayList<>();
		String closing = null;
		for (String line : lines) {
			StringBuilder text = new StringBuilder();
			int i = 0;
			if (closing != null) {
				int end = line.indexOf(closing);
				if (end < 0) {
					kept.add("");
					continue;
				}
				i = end + closing.length();
				closing = null;
			}
			char quote = 0;
			for (; i < line.length(); i++) {
				char c = line.charAt(i);
				if (quote != 0) {
					text.append(c);
					if (c == '\\' && quote == '"' && i + 1 < line.length()) {
						text.append(line.charAt(++i));
					} else if (c == quote) {
						quote = 0;
					}
				} else if (line.startsWith("\"\"\"", i) || line.startsWith("'''", i)) {
					String delimiter = line.substring(i, i + 3);
					int end = line.indexOf(delimiter, i + 3);
					if (end < 0) {
						closing = delimiter;
						break;
					}
					text.append('"').append(line, i + 3, end).append('"');
					i = end + 2;
				} else if (c == '"' || c == '\'') {
					quote = c;
					text.append(c);
				} else if (c == '#') {
					break;
				} else {
					text.append(c);
				}
			}
			kept.add(text.toString());
		}
		return kept;
	}

	/** One line without a TOML comment: from a # that isn't inside a string to its end. */
	static String withoutComment(String line) {
		return configuration(List.of(line)).getFirst();
	}

	/** Called jobwarden, or at this computer's /mcp, whatever the port. */
	private boolean isOurs(String name, @Nullable String url) {
		if (SERVER_NAME.equalsIgnoreCase(name.strip())) {
			return true;
		}
		if (url == null || url.isBlank()) {
			return false;
		}
		try {
			URI address = URI.create(url.strip());
			String host = address.getHost();
			String path = (address.getPath() == null) ? "" : address.getPath().replaceAll("/+$", "");
			return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT)) && path.equals(this.mcpPath);
		} catch (IllegalArgumentException notAnAddress) {
			return false;
		}
	}

}
