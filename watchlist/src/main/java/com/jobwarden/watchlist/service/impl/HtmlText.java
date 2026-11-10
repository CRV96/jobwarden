package com.jobwarden.watchlist.service.impl;

import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.jspecify.annotations.Nullable;

/**
 * A posting's HTML as the text the AI Assistant page shows: its paragraphs on lines of their own, its lists' items
 * with a bullet. Nothing of the HTML is kept, so nothing of it runs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HtmlText {

	private static final Pattern SPACES_BEFORE_BREAK = Pattern.compile("[ \\t]+\\n");

	private static final Pattern SPACES_AFTER_BREAK = Pattern.compile("\\n[ \\t]+");

	private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

	/**
	 * @return the text; {@code null} when there's none
	 */
	static @Nullable String of(@Nullable String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		new NodeVisitor() {

			@Override
			public void head(Node node, int depth) {
				if (node instanceof TextNode words) {
					text.append(words.text());
				} else if (node instanceof Element element) {
					if (element.nameIs("br")) {
						text.append('\n');
					} else if (element.nameIs("li")) {
						text.append("\n• ");
					} else if (element.isBlock()) {
						text.append("\n\n");
					}
				}
			}

		}.traverse(Jsoup.parseBodyFragment(html).body());
		String tidy = BLANK_LINES.matcher(SPACES_AFTER_BREAK.matcher(SPACES_BEFORE_BREAK.matcher(text).replaceAll("\n"))
			.replaceAll("\n")).replaceAll("\n\n").strip();
		return tidy.isEmpty() ? null : tidy;
	}

}
