package com.jobwarden.updates.service.impl;

import org.jspecify.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * A release's notes as GitHub makes them of their Markdown (its {@code body_html}), cleaned to show in a page as they
 * are: headings, paragraphs, lists, quotes, code, tables and links, which open in a new tab. No scripts, styles,
 * images, forms, classes or GitHub's own attributes; a link only to http(s) or mail, a relative one made absolute on
 * github.com. The page trusts what this leaves, so it's the one place release notes are cleaned.
 */
final class ReleaseNotes {

	private static final Safelist FORMATTING = Safelist.relaxed()
		.removeTags("img")
		.removeProtocols("a", "href", "ftp")
		.addEnforcedAttribute("a", "target", "_blank")
		.addEnforcedAttribute("a", "rel", "noopener noreferrer");

	private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

	private ReleaseNotes() {
	}

	/**
	 * @param html the notes, as GitHub made them
	 * @return the notes cleaned, or {@code null} when nothing's left to show
	 */
	static @Nullable String clean(String html) {
		if (html.isBlank()) {
			return null;
		}
		String cleaned = Jsoup.clean(html, "https://github.com/", FORMATTING, OUTPUT).strip();
		return Jsoup.parse(cleaned).text().isBlank() ? null : cleaned;
	}

}
