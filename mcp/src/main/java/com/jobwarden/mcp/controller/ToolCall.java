package com.jobwarden.mcp.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * A tool's arguments, with the profile the client named in its header, if any.
 *
 * @param profileHeader the {@code X-JobWarden-Profile} header: the profile to use when the arguments don't name one
 */
record ToolCall(JsonNode arguments, @Nullable Long profileHeader) {

	@Nullable String string(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new ToolException(name + " must be a string");
		}
		String text = value.asString().strip();
		return text.isEmpty() ? null : text;
	}

	String requiredString(String name) {
		String value = string(name);
		if (value == null) {
			throw new ToolException(name + " is required");
		}
		return value;
	}

	@Nullable Long number(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber()) {
			throw new ToolException(name + " must be a whole number");
		}
		return value.asLong();
	}

	long requiredNumber(String name) {
		Long value = number(name);
		if (value == null) {
			throw new ToolException(name + " is required");
		}
		return value;
	}

	int limit(String name, int byDefault, int max) {
		Long value = number(name);
		return (value == null) ? byDefault : (int) Math.clamp(value, 1, max);
	}

	@Nullable LocalDate date(String name) {
		String value = string(name);
		try {
			return (value == null) ? null : LocalDate.parse(value);
		} catch (DateTimeParseException notADate) {
			throw new ToolException(name + " must be a date as YYYY-MM-DD");
		}
	}

	@Nullable LocalTime time(String name) {
		String value = string(name);
		try {
			return (value == null) ? null : LocalTime.parse(value);
		} catch (DateTimeParseException notATime) {
			throw new ToolException(name + " must be a time of day as HH:MM");
		}
	}

	<E extends Enum<E>> @Nullable E constant(String name, Class<E> type) {
		String value = string(name);
		return (value == null) ? null : constant(name, value, type);
	}

	List<String> strings(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || value.isNull()) {
			return List.of();
		}
		if (!value.isArray()) {
			throw new ToolException(name + " must be a list of strings");
		}
		List<String> strings = new ArrayList<>();
		for (JsonNode item : value) {
			if (!item.isString()) {
				throw new ToolException(name + " must be a list of strings");
			}
			strings.add(item.asString());
		}
		return strings;
	}

	<E extends Enum<E>> List<E> constants(String name, Class<E> type) {
		return strings(name).stream().map(value -> constant(name, value, type)).toList();
	}

	List<JsonNode> objects(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || !value.isArray() || value.isEmpty()) {
			throw new ToolException(name + " must be a list with at least one item");
		}
		List<JsonNode> objects = new ArrayList<>();
		value.forEach(objects::add);
		return objects;
	}

	/**
	 * An object, read with the same helpers.
	 */
	ToolCall requiredObject(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || !value.isObject()) {
			throw new ToolException(name + " is required, as an object");
		}
		return new ToolCall(value, this.profileHeader);
	}

	/**
	 * A list of objects that may be missing or empty, each read with the same helpers.
	 */
	List<ToolCall> items(String name) {
		JsonNode value = this.arguments.get(name);
		if (value == null || value.isNull()) {
			return List.of();
		}
		if (!value.isArray()) {
			throw new ToolException(name + " must be a list");
		}
		List<ToolCall> items = new ArrayList<>();
		value.forEach(item -> items.add(item(item)));
		return items;
	}

	/** One item of a list of objects, read with the same helpers. */
	ToolCall item(JsonNode item) {
		if (!item.isObject()) {
			throw new ToolException("Each item must be an object");
		}
		return new ToolCall(item, this.profileHeader);
	}

	private static <E extends Enum<E>> E constant(String name, String value, Class<E> type) {
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw new ToolException(name + " must be one of " + Arrays.toString(type.getEnumConstants()));
		}
	}

}
