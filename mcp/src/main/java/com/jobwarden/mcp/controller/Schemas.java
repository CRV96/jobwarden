package com.jobwarden.mcp.controller;

import java.util.Arrays;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON Schemas for the tools' arguments, as short as they can be: the assistant reads every tool's schema in every
 * session. No additionalProperties: arguments the tools don't know are left unread anyway.
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
		return schema;
	}

	/**
	 * An object, with what it is: for an argument whose name alone doesn't say it.
	 */
	static ObjectNode object(String description, Map<String, ObjectNode> properties, String... required) {
		ObjectNode schema = object(properties, required);
		return schema.put("description", description);
	}

	static ObjectNode string(String description) {
		return string().put("description", description);
	}

	/** A string whose name says what it is, e.g. company or email. */
	static ObjectNode string() {
		return NODES.objectNode().put("type", "string");
	}

	static ObjectNode date(String description) {
		return string(description).put("format", "date");
	}

	/** A date whose name says what it is, e.g. deadline. */
	static ObjectNode date() {
		return string().put("format", "date");
	}

	static ObjectNode bool(String description) {
		return bool().put("description", description);
	}

	/** A yes or no whose name says what it is, e.g. followed_only. */
	static ObjectNode bool() {
		return NODES.objectNode().put("type", "boolean");
	}

	static ObjectNode integer(String description) {
		return integer().put("description", description);
	}

	/** A whole number whose name says what it is, e.g. job_id. */
	static ObjectNode integer() {
		return NODES.objectNode().put("type", "integer");
	}

	static ObjectNode oneOf(Enum<?>[] values, String description) {
		return oneOf(values).put("description", description);
	}

	/** One of the values, which say what they are. */
	static ObjectNode oneOf(Enum<?>[] values) {
		return oneOf(Arrays.stream(values).map(Enum::name).toArray(String[]::new));
	}

	/** One of these names, e.g. constants shown to the assistant by other names than their own. */
	static ObjectNode oneOf(String... values) {
		ObjectNode schema = string();
		ArrayNode names = schema.putArray("enum");
		Arrays.stream(values).forEach(names::add);
		return schema;
	}

	static ObjectNode list(ObjectNode items, String description) {
		return list(items).put("description", description);
	}

	/** A list whose name says what it holds, e.g. jobs. */
	static ObjectNode list(ObjectNode items) {
		ObjectNode schema = NODES.objectNode().put("type", "array");
		schema.set("items", items);
		return schema;
	}

	static ObjectNode strings(String description) {
		return list(string(), description);
	}

	/** Strings whose name says what they are, e.g. skills. */
	static ObjectNode strings() {
		return list(string());
	}

}
