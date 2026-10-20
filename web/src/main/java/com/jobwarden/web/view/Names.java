package com.jobwarden.web.view;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Profile names as the templates show them: {@code ${@names.initials(profile.name)}}.
 */
@Component("names")
public class Names {

	private static final int MAX_INITIALS = 2;

	/**
	 * For a profile's avatar: the first letters of its first two words, e.g. RC for Robert Ciapa, R for Robert.
	 */
	public String initials(String name) {
		return Arrays.stream(name.strip().split("\\s+"))
			.filter(word -> !word.isEmpty())
			.limit(MAX_INITIALS)
			.map(word -> Character.toString(word.codePointAt(0)))
			.collect(Collectors.joining())
			.toUpperCase(Locale.ROOT);
	}

}
