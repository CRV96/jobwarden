package com.jobwarden.mcp.controller;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobwarden.common.exception.JobWardenException;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The MCP endpoint: JSON-RPC 2.0 over HTTP, as the protocol's Streamable HTTP transport has it, answering each request
 * with JSON. It keeps no session: every request stands on its own, and it never streams, so GET is refused.
 * <p>
 * Only the app's own machine may call it. It answers no browser page from another site (the Origin header), which
 * could otherwise reach a server on localhost through the visitor's browser.
 */
@RestController
@RequestMapping("${jobwarden.mcp.path}")
@CustomLog
class McpController {

	/** The profile for tools that aren't given one: set once in the client's configuration. */
	static final String PROFILE_HEADER = "X-JobWarden-Profile";

	/**
	 * Says no one watches the client: the terminal loop and Run, which give the assistant their own configuration with
	 * it. Auto-apply's requests are then out of its reach, as they need the profile at the browser.
	 */
	static final String UNATTENDED_HEADER = "X-JobWarden-Unattended";

	/** Who's calling, for the logs, when the User-Agent doesn't say. */
	static final String UNKNOWN_CLIENT = "An MCP client";

	/** The most of a client's own name the logs keep, when it's neither Claude Code nor Codex. */
	private static final int CLIENT_MAX_LENGTH = 40;

	/** The protocol versions it speaks, newest first. */
	static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

	/**
	 * Sent once, as the session starts, so short, and for any assistant: each request's own steps come with it
	 * (get_request). The second paragraph saves the most tokens, in every request.
	 */
	static final String INSTRUCTIONS = """
			JobWarden tracks a job hunt: jobs with a status and a timeline. The profile asks for work from its pages as \
			requests: list_requests lists those waiting, get_request gives one's steps and the data they need, \
			finish_request ends it with a summary the page shows.
			Spare the context: adding jobs, posts or recruiters skips the links the profile has, so never list them \
			first. Pass search results' links to filter_links and open only the new ones whose title and snippet fit. \
			Read a page with a short question (e.g. WebFetch), not whole; check_postings with urls tells open postings \
			from closed ones, many at once.
			Messages to recruiters are drafts the profile sends itself: never send them. A cover letter goes out only \
			with an application, when Auto-apply's steps say so.""";

	/** Times to the minute, e.g. 2026-11-03T09:56Z: seconds and their fractions would only cost tokens. */
	private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
		.withZone(ZoneOffset.UTC);

	private final Map<String, McpTool> tools;

	private final JsonMapper json;

	private final String version;

	McpController(List<McpToolSet> toolSets, JsonMapper json, @Value("${jobwarden.version:dev}") String version) {
		this.tools = toolSets.stream()
			.flatMap(set -> set.tools().stream())
			.collect(Collectors.toMap(McpTool::name, Function.identity(), (a, b) -> {
				throw new IllegalStateException("Two tools named " + a.name());
			}, LinkedHashMap::new));
		this.json = results(json);
		this.version = version;
	}

	/**
	 * How results are written, which RequestTools also measures them with: in snake_case like the arguments, without
	 * the empty fields, and times to the minute, as the assistant reads every character.
	 */
	static JsonMapper results(JsonMapper json) {
		return json.rebuild()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
			.addModule(new SimpleModule("mcp-times").addSerializer(Instant.class, new ValueSerializer<Instant>() {

				@Override
				public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
					generator.writeString(MINUTES.format(value));
				}

			}))
			.build();
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<JsonNode> call(@RequestBody String body, @RequestHeader(name = HttpHeaders.ORIGIN, required = false)
			@Nullable String origin, @RequestHeader(name = PROFILE_HEADER, required = false) @Nullable String profile,
			@RequestHeader(name = UNATTENDED_HEADER, required = false) @Nullable String unattended,
			@RequestHeader(name = HttpHeaders.USER_AGENT, required = false) @Nullable String userAgent) {
		if (!isLocal(origin)) {
			log.warn("Refused an MCP request from {}", origin);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		JsonNode message;
		try {
			message = this.json.readTree(body);
		} catch (JacksonException notJson) {
			return answer(error(null, -32700, "Parse error: the body isn't JSON"));
		}
		if (!message.isObject() || !"2.0".equals(message.path("jsonrpc").asString(null))) {
			return answer(error(null, -32600, "Invalid request: a JSON-RPC 2.0 object was expected"));
		}
		JsonNode id = message.get("id");
		if (id == null || id.isNull()) {
			// A notification, such as notifications/initialized, or a response: nothing to answer
			return ResponseEntity.accepted().build();
		}
		String method = message.path("method").asString("");
		JsonNode params = message.path("params");
		return answer(switch (method) {
			case "initialize" -> result(id, initialize(params));
			case "ping" -> result(id, JsonNodeFactory.instance.objectNode());
			case "tools/list" -> result(id, toolList());
			case "tools/call" -> callTool(id, params, profile, client(userAgent), "true".equalsIgnoreCase(unattended));
			default -> error(id, -32601, "Method not found: " + method);
		});
	}

	/** It never streams: no events to listen for. */
	@GetMapping
	ResponseEntity<Void> stream() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
	}

	/** It keeps no session to end. */
	@DeleteMapping
	ResponseEntity<Void> endSession() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
	}

	/** The client's version when it's one of these, else the newest: the client then decides whether to go on. */
	private ObjectNode initialize(JsonNode params) {
		String asked = params.path("protocolVersion").asString("");
		ObjectNode result = JsonNodeFactory.instance.objectNode()
			.put("protocolVersion", PROTOCOL_VERSIONS.contains(asked) ? asked : PROTOCOL_VERSIONS.getFirst());
		result.putObject("capabilities").putObject("tools").put("listChanged", false);
		result.putObject("serverInfo").put("name", "jobwarden").put("title", "JobWarden").put("version", this.version);
		return result.put("instructions", INSTRUCTIONS);
	}

	/**
	 * Each tool once: its title for people, at the top only, and the hints clients decide approvals by. Codex, e.g.,
	 * asks before a tool that isn't read-only and reaches the web, and refuses it in codex exec.
	 */
	private ObjectNode toolList() {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		ArrayNode list = result.putArray("tools");
		for (McpTool tool : this.tools.values()) {
			ObjectNode entry = list.addObject().put("name", tool.name()).put("title", tool.title())
				.put("description", tool.description());
			entry.set("inputSchema", tool.inputSchema());
			entry.putObject("annotations")
				.put("readOnlyHint", tool.readOnly())
				.put("destructiveHint", false)
				.put("openWorldHint", tool.openWorld());
		}
		return result;
	}

	/**
	 * A tool that fails because of what it was asked, such as a missing argument or a job that doesn't exist, answers
	 * with isError and the reason, which the assistant reads; an unknown tool is a protocol error.
	 */
	private ObjectNode callTool(JsonNode id, JsonNode params, @Nullable String profile, String client, boolean unattended) {
		String name = params.path("name").asString("");
		McpTool tool = this.tools.get(name);
		if (tool == null) {
			return error(id, -32602, "Unknown tool: " + name);
		}
		JsonNode arguments = params.path("arguments");
		ToolCall call = new ToolCall(arguments.isObject() ? arguments : JsonNodeFactory.instance.objectNode(), profile, client,
				unattended);
		log.debug("{} calls {}", client, name);
		try {
			return result(id, toolResult(this.json.writeValueAsString(tool.handler().call(call)), false));
		} catch (ToolException | JobWardenException | IllegalArgumentException refused) {
			log.debug("MCP tool {} refused: {}", name, refused.getMessage());
			return result(id, toolResult(Objects.requireNonNullElse(refused.getMessage(), "Refused"), true));
		}
	}

	/**
	 * The result as JSON text, once: the tools declare no output schema, and the same result again as
	 * structuredContent would only take up the assistant's context a second time.
	 */
	private static ObjectNode toolResult(String text, boolean isError) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.putArray("content").addObject().put("type", "text").put("text", text);
		return result.put("isError", isError);
	}

	private static ObjectNode result(JsonNode id, JsonNode result) {
		ObjectNode response = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0");
		response.set("id", id);
		response.set("result", result);
		return response;
	}

	private static ObjectNode error(@Nullable JsonNode id, int code, String message) {
		ObjectNode response = JsonNodeFactory.instance.objectNode().put("jsonrpc", "2.0");
		response.set("id", (id == null) ? JsonNodeFactory.instance.nullNode() : id);
		response.putObject("error").put("code", code).put("message", message);
		return response;
	}

	private static ResponseEntity<JsonNode> answer(ObjectNode response) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
	}

	/**
	 * Who's calling, from its User-Agent, so the logs say which assistant did what: Claude Code sends
	 * claude-code/2.1.311 (…), Codex codex-mcp-client/0.1.0. Another client goes by the first word it sends, kept to
	 * letters, digits and a few signs, as it lands in the logs.
	 */
	static String client(@Nullable String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return UNKNOWN_CLIENT;
		}
		String agent = userAgent.strip().toLowerCase(Locale.ROOT);
		if (agent.startsWith("claude-code") || agent.startsWith("claude_code")) {
			return "Claude Code";
		}
		if (agent.startsWith("codex")) {
			return "Codex";
		}
		String product = userAgent.strip().split("[/\\s]", 2)[0].replaceAll("[^A-Za-z0-9._-]", "");
		return product.isEmpty() ? UNKNOWN_CLIENT
				: product.substring(0, Math.min(product.length(), CLIENT_MAX_LENGTH));
	}

	/** No Origin is a client that isn't a browser page, like Claude Code or Codex; a page must be on this machine. */
	private static boolean isLocal(@Nullable String origin) {
		if (origin == null || origin.isBlank()) {
			return true;
		}
		try {
			String host = URI.create(origin).getHost();
			return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException notAnOrigin) {
			return false;
		}
	}

}
