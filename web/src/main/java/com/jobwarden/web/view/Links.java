package com.jobwarden.web.view;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Text with its web addresses as links, for the templates (applications/fragments :: linked-text): the text in parts,
 * each written escaped with {@code th:text}, an address as a link with {@code th:href}. Nothing of the text is ever
 * written as HTML, as a posting's text, a note on a timeline or a recruiter's post may hold anything. Only
 * {@code http(s)} addresses become links, which open in a new tab and tell the site nothing of where they came from.
 */
@Component("links")
public class Links {

	/** An address, up to the first space, quote or angle bracket. */
	private static final Pattern ADDRESS = Pattern.compile("https?://[^\\s<>\"'`]+", Pattern.CASE_INSENSITIVE);

	/** What ends a sentence rather than the address it follows. */
	private static final String TRAILING = ".,;:!?)]}*";

	/**
	 * A part of the text: words, or a web address to link.
	 *
	 * @param link whether it's an {@code http(s)} address
	 */
	public record Part(String text, boolean link) {
	}

	/**
	 * @return the text in parts, in order, the web addresses apart; none for no text
	 */
	public List<Part> parts(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		List<Part> parts = new ArrayList<>();
		Matcher matcher = ADDRESS.matcher(text);
		int written = 0;
		while (matcher.find()) {
			String address = trimmed(matcher.group());
			if (matcher.start() > written) {
				parts.add(new Part(text.substring(written, matcher.start()), false));
			}
			parts.add(new Part(address, true));
			written = matcher.start() + address.length();
		}
		if (written < text.length()) {
			parts.add(new Part(text.substring(written), false));
		}
		return parts;
	}

	/**
	 * The address without what follows it in a sentence: "see https://acme.com/jobs." links acme.com/jobs. A closing
	 * bracket stays when the address opens one, as Wikipedia's do.
	 */
	private static String trimmed(String address) {
		String trimmed = address;
		while (!trimmed.isEmpty() && TRAILING.indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
			char last = trimmed.charAt(trimmed.length() - 1);
			if (last == ')' && count(trimmed, '(') >= count(trimmed, ')')) {
				break;
			}
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static long count(String text, char character) {
		return text.chars().filter(found -> found == character).count();
	}

}
