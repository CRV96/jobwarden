package com.jobwarden.cv.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The kinds of file a CV can be. The extension or the type the browser sent decides, and the content must agree: a PDF
 * starts with {@code %PDF}, a Word document is a ZIP file.
 */
enum CvFileType {

	PDF("application/pdf", List.of(".pdf"), "%PDF"),
	DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", List.of(".docx"), "PK"),
	TEXT("text/plain", List.of(".txt", ".md"), null);

	private final String contentType;

	private final List<String> extensions;

	private final @Nullable String startsWith;

	CvFileType(String contentType, List<String> extensions, @Nullable String startsWith) {
		this.contentType = contentType;
		this.extensions = extensions;
		this.startsWith = startsWith;
	}

	String contentType() {
		return this.contentType;
	}

	/**
	 * @return the type the file's name or content type says it is, or empty when it's none of them
	 */
	static Optional<CvFileType> claimedBy(String fileName, @Nullable String contentType) {
		String name = fileName.toLowerCase(Locale.ROOT);
		return Arrays.stream(values())
			.filter(type -> type.extensions.stream().anyMatch(name::endsWith) || type.contentType.equals(contentType))
			.findFirst();
	}

	/**
	 * @return whether the content is of this type, as far as its first bytes tell: a file can be named .pdf and not be
	 * one
	 */
	boolean matches(byte[] content) {
		if (this.startsWith == null) {
			return true;
		}
		byte[] expected = this.startsWith.getBytes(StandardCharsets.US_ASCII);
		return content.length >= expected.length && Arrays.equals(content, 0, expected.length, expected, 0, expected.length);
	}

}
