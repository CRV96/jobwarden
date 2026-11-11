package com.jobwarden.web.view;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Text with its web addresses as links, for the templates: {@code th:utext="${@links.html(job.description)}"}. Every
 * part of the text is escaped, the addresses too, and only {@code http(s)} addresses become links, which open in a new
 * tab and tell the site nothing of where they came from. A posting's text, a note on a timeline or a recruiter's post
 * may hold anything, so nothing in it is ever taken as HTML.
 */
@Component("links")
public class Links {

	/** An address, up to the first space, quote or angle bracket. */
	private static final Pattern ADDRESS = Pattern.compile("https?://[^\\s<>\"'`]+", Pattern.CASE_INSENSITIVE);

	/** What ends a sentence rather than the address it follows. */
	private static final String TRAILING = ".,;:!?)]}*";

	/**
	 * @return the text, escaped, with each web address a link; empty for none
	 */
	public String html(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder html = new StringBuilder(text.length() + 64);
		Matcher matcher = ADDRESS.matcher(text);
		int written = 0;
		while (matcher.find()) {
			String address = trimmed(matcher.group());
			int end = matcher.start() + address.length();
			html.append(HtmlUtils.htmlEscape(text.substring(written, matcher.start())));
			String escaped = HtmlUtils.htmlEscape(address);
			html.append("<a class=\"link break-all\" href=\"")
				.append(escaped)
				.append("\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">")
				.append(escaped)
				.append("</a>");
			written = end;
		}
		html.append(HtmlUtils.htmlEscape(text.substring(written)));
		return html.toString();
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
