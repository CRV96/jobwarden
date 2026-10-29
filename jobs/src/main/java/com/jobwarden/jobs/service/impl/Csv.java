package com.jobwarden.jobs.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * CSV as RFC 4180 has it: a value is quoted when it holds the delimiter, a quote (doubled) or a line break. Reads what
 * spreadsheets save too: a byte order mark, and semicolons or tabs between the values, as Excel writes them where the
 * comma is the decimal separator.
 */
final class Csv {

	static final char BYTE_ORDER_MARK = '﻿';

	private static final String LINE_END = "\r\n";

	private Csv() {
	}

	/**
	 * @return the records, each a list of its values; blank lines are left out
	 */
	static List<List<String>> parse(String text) {
		String content = (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) ? text.substring(1) : text;
		char delimiter = delimiterOf(content);
		List<List<String>> records = new ArrayList<>();
		List<String> values = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (quoted) {
				if (c != '"') {
					value.append(c);
				} else if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
					value.append('"');
					i++;
				} else {
					quoted = false;
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == delimiter) {
				values.add(value.toString());
				value.setLength(0);
			} else if (c == '\n') {
				addRecord(records, values, value);
				values = new ArrayList<>();
			} else if (c != '\r') {
				value.append(c);
			}
		}
		addRecord(records, values, value);
		return records;
	}

	/**
	 * Writes one record, and the line's end.
	 */
	static void write(Appendable out, List<@Nullable String> values) throws IOException {
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(quote(values.get(i)));
		}
		out.append(LINE_END);
	}

	/**
	 * Takes back what {@link #write} added in front of a value a spreadsheet would run as a formula.
	 */
	static String unprotect(String value) {
		return (value.length() > 1 && value.charAt(0) == '\'' && isFormula(value.substring(1))) ? value.substring(1)
				: value;
	}

	private static void addRecord(List<List<String>> records, List<String> values, StringBuilder value) {
		values.add(value.toString());
		value.setLength(0);
		if (values.stream().anyMatch(field -> !field.isBlank())) {
			records.add(values);
		}
	}

	/**
	 * The character that separates the most values on the first line, quotes left out: a comma, a semicolon or a tab.
	 */
	private static char delimiterOf(String content) {
		int commas = 0;
		int semicolons = 0;
		int tabs = 0;
		boolean quoted = false;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (c == '\n' && !quoted) {
				break;
			}
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted) {
				commas += (c == ',') ? 1 : 0;
				semicolons += (c == ';') ? 1 : 0;
				tabs += (c == '\t') ? 1 : 0;
			}
		}
		if (semicolons > commas && semicolons >= tabs) {
			return ';';
		}
		return (tabs > commas) ? '\t' : ',';
	}

	/**
	 * Quotes the value when it needs it. One a spreadsheet would run as a formula starts with an apostrophe, which
	 * spreadsheets don't show, so a posting can't run a formula on the computer that opens the file.
	 */
	private static String quote(@Nullable String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String safe = isFormula(value) ? "'" + value : value;
		boolean needsQuotes = safe.chars().anyMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r' || c == ';')
				|| !safe.equals(safe.strip());
		return needsQuotes ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
	}

	/**
	 * Starts like a formula: with =, +, @, a tab or a carriage return, or a minus before something that isn't a
	 * number or a space, so "- Java" and "-5" stay as they are.
	 */
	private static boolean isFormula(String value) {
		char first = value.charAt(0);
		if (first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r') {
			return true;
		}
		return first == '-' && value.length() > 1 && !Character.isDigit(value.charAt(1))
				&& !Character.isWhitespace(value.charAt(1));
	}

}
