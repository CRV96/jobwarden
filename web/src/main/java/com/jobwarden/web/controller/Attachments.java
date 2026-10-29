package com.jobwarden.web.controller;

import java.nio.charset.StandardCharsets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Files to download.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Attachments {

	/**
	 * A file to download, under its name, which may have any letters: browsers that only read ASCII names get one
	 * with the others left out.
	 */
	static ResponseEntity<byte[]> of(byte[] content, MediaType type, String fileName) {
		return ResponseEntity.ok()
			.contentType(type)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
			.body(content);
	}

	/**
	 * A CV's PDF name: "CV Ana Pop - Acme.pdf" for a job at Acme, "CV Ana Pop.pdf" for none.
	 */
	static String cvName(@Nullable String fullName, @Nullable String company) {
		StringBuilder name = new StringBuilder("CV");
		if (fullName != null) {
			name.append(' ').append(fullName);
		}
		if (company != null) {
			name.append(" - ").append(company);
		}
		return name.append(".pdf").toString();
	}

}
