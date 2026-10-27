package com.jobwarden.mcp.controller;

import java.util.Arrays;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON Schemas for the tools' arguments.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Schemas {

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	/**
	 * @param properties each argument's schema, in the order given
	 */
	static ObjectNode object(Map<String, ObjectNode> properties, String... required) {
		ObjectNode schema = NODES.objectNode().put("type", "object");
		ObjectNode props = schema.putObject("properties");
		properties.forEach(props::set);
		if (required.length > 0) {
			ArrayNode names = schema.putArray("required");
			Arrays.stream(required).forEach(names::add);
		}
		return schema.put("additionalProperties", false);
	}

	static ObjectNode string(String description) {
		return NODES.objectNode().put("type", "string").put("description", description);
	}

	static ObjectNode date(String description) {
		return string(description).put("format", "date");
	}

	static ObjectNode bool(String description) {
		return NODES.objectNode().put("type", "boolean").put("description", description);
	}

	static ObjectNode integer(String description) {
		return NODES.objectNode().put("type", "integer").put("description", description);
	}

	static ObjectNode oneOf(Enum<?>[] values, String description) {
		ObjectNode schema = string(description);
		ArrayNode names = schema.putArray("enum");
		Arrays.stream(values).map(Enum::name).forEach(names::add);
		return schema;
	}

	static ObjectNode list(ObjectNode items, String description) {
		ObjectNode schema = NODES.objectNode().put("type", "array").put("description", description);
		schema.set("items", items);
		return schema;
	}

	static ObjectNode strings(String description) {
		return list(NODES.objectNode().put("type", "string"), description);
	}

}
