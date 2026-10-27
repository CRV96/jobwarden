package com.jobwarden.mcp.controller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.JobWardenException;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.annotation.JsonInclude;

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

	/** The protocol versions it speaks, newest first. */
	static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

	private static final String INSTRUCTIONS = """
			JobWarden keeps a job hunt: the jobs saved or applied to, each with a status and a timeline.
			Checking postings: call check_postings, which fetches each posting's page. Move a job to POSITION_CLOSED \
			only when the check is certainly CLOSED, or when you saw the posting gone yourself; open the UNKNOWN ones \
			yourself if you can. close_when_gone does it for the Saved and Applied jobs in one call.
			Finding jobs: list_saved_searches gives the profile's searches with queries ready to run; run them with \
			your web search, open the results, and add the real, open postings with add_found_jobs, each with a note \
			on why it fits. They wait on the Discover page, where the profile saves, applies to or discards them; \
			list_found_jobs shows what was decided, to learn what the profile likes.""";

	private final Map<String, McpTool> tools;

	private final JsonMapper json;

	private final String version;

	McpController(List<McpToolSet> toolSets, JsonMapper json, @Value("${jobwarden.version:dev}") String version) {
		this.tools = toolSets.stream()
			.flatMap(set -> set.tools().stream())
			.collect(Collectors.toMap(McpTool::name, Function.identity(), (a, b) -> {
				throw new IllegalStateException("Two tools named " + a.name());
			}, LinkedHashMap::new));
		// Results in snake_case like the arguments, without the empty fields
		this.json = json.rebuild()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
			.build();
		this.version = version;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<JsonNode> call(@RequestBody String body, @RequestHeader(name = HttpHeaders.ORIGIN, required = false)
			@Nullable String origin, @RequestHeader(name = PROFILE_HEADER, required = false) @Nullable Long profile) {
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
			case "tools/call" -> callTool(id, params, profile);
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

	private ObjectNode toolList() {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		ArrayNode list = result.putArray("tools");
		for (McpTool tool : this.tools.values()) {
			ObjectNode entry = list.addObject().put("name", tool.name()).put("title", tool.title())
				.put("description", tool.description());
			entry.set("inputSchema", tool.inputSchema());
			entry.putObject("annotations")
				.put("title", tool.title())
				.put("readOnlyHint", tool.readOnly())
				.put("destructiveHint", false)
				.put("openWorldHint", tool.openWorld());
		}
		return result;
	}

	/**
	 * A tool that fails because of what it was asked, such as a missing argument or a job that doesn't exist, answers
	 * with isError and the reason, which Claude reads; an unknown tool is a protocol error.
	 */
	private ObjectNode callTool(JsonNode id, JsonNode params, @Nullable Long profile) {
		String name = params.path("name").asString("");
		McpTool tool = this.tools.get(name);
		if (tool == null) {
			return error(id, -32602, "Unknown tool: " + name);
		}
		JsonNode arguments = params.path("arguments");
		ToolCall call = new ToolCall(arguments.isObject() ? arguments : JsonNodeFactory.instance.objectNode(), profile);
		try {
			JsonNode structured = this.json.valueToTree(tool.handler().call(call));
			return result(id, toolResult(this.json.writeValueAsString(structured), structured, false));
		} catch (ToolException | JobWardenException | IllegalArgumentException refused) {
			log.debug("MCP tool {} refused: {}", name, refused.getMessage());
			return result(id, toolResult(refused.getMessage(), null, true));
		}
	}

	private static ObjectNode toolResult(String text, @Nullable JsonNode structured, boolean isError) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.putArray("content").addObject().put("type", "text").put("text", text);
		if (structured != null && structured.isObject()) {
			result.set("structuredContent", structured);
		}
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

	/** No Origin is a client that isn't a browser page, like Claude; a page must be on this machine. */
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
