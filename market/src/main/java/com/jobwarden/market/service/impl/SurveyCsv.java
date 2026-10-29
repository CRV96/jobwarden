package com.jobwarden.market.service.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Reads CSV as RFC 4180 has it, one record at a time: fields separated by commas, quoted when they have a comma, a
 * quote (doubled) or a line break. The survey's answers have all three, and fields longer than most parsers allow.
 */
final class SurveyCsv {

	private final Reader reader;

	private int next;

	SurveyCsv(Reader reader) throws IOException {
		this.reader = reader;
		this.next = reader.read();
	}

	/**
	 * @return the next record's fields, or {@code null} at the end
	 */
	@Nullable List<String> read() throws IOException {
		if (this.next == -1) {
			return null;
		}
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		while (this.next != -1) {
			int c = this.next;
			this.next = this.reader.read();
			if (quoted) {
				if (c != '"') {
					field.append((char) c);
				} else if (this.next == '"') {
					field.append('"');
					this.next = this.reader.read();
				} else {
					quoted = false;
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == ',') {
				fields.add(field.toString());
				field.setLength(0);
			} else if (c == '\n') {
				break;
			} else if (c != '\r') {
				field.append((char) c);
			}
		}
		fields.add(field.toString());
		return fields;
	}

}
