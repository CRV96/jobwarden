package com.jobwarden.web.view;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * A job's description as its page shows it (applications/fragments :: description): paragraphs, lists, bold and
 * italics, which the profile gives it on the page and which are kept as Markdown, so the text reads the same to the AI
 * Assistant, in a CSV file or where it's shared: {@code **bold**}, {@code *italics*}, and a line starting with
 * {@code - }, {@code * } or {@code 1. } for a point of a list. A posting saved as plain text shows as it was, its
 * {@code •} lines as a list, and its web addresses as links, as {@link Links} makes them.
 * <p>
 * Everything of the text is escaped before a tag is made, and what's made goes through jsoup's allow-list as well: a
 * description shows these tags alone, and links only to http(s) addresses, in a new tab.
 */
@Component("richText")
@RequiredArgsConstructor
public class RichText {

	/** A point of a list: a dash, a star or a bullet, and a space. Not {@code *italics*} at the line's start. */
	private static final Pattern BULLET = Pattern.compile("^\\s*[-*•]\\s+(.*)$");

	private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d{1,3}[.)]\\s+(.*)$");

	private static final Pattern BOLD = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*");

	/** One star each side, not two, and not inside a word: 5*3*2 stays as it is. */
	private static final Pattern ITALICS = Pattern.compile("(?<![*\\w])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![*\\w])");

	/** Where a web address was, while the rest of the line is escaped and formatted: no text has this character. */
	private static final char LINK_MARK = '\u0000';

	private static final Pattern LINK_PLACE = Pattern.compile(LINK_MARK + "(\\d+)" + LINK_MARK);

	private static final Safelist ALLOWED = Safelist.none()
		.addTags("p", "br", "strong", "em", "ul", "ol", "li", "a")
		.addAttributes("a", "href", "class")
		.addProtocols("a", "href", "http", "https")
		.addEnforcedAttribute("a", "target", "_blank")
		.addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow");

	private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

	private final Links links;

	/**
	 * @return the text as HTML to write as it is ({@code th:utext}); empty for no text
	 */
	public String html(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		Blocks blocks = new Blocks();
		for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
			Matcher bullet = BULLET.matcher(line);
			Matcher numbered = NUMBERED.matcher(line);
			if (bullet.matches()) {
				blocks.point("ul", inline(bullet.group(1).strip()));
			} else if (numbered.matches()) {
				blocks.point("ol", inline(numbered.group(1).strip()));
			} else if (line.isBlank()) {
				blocks.end();
			} else {
				blocks.line(inline(line.strip()));
			}
		}
		blocks.end();
		return Jsoup.clean(blocks.html.toString(), "", ALLOWED, OUTPUT);
	}

	/**
	 * A line's words escaped, its web addresses as links, and its bold and italics as tags.
	 */
	private String inline(String line) {
		List<String> addresses = new ArrayList<>();
		StringBuilder marked = new StringBuilder();
		for (Links.Part part : this.links.parts(line.replace(LINK_MARK, ' '))) {
			if (part.link()) {
				marked.append(LINK_MARK).append(addresses.size()).append(LINK_MARK);
				addresses.add(part.text());
			} else {
				marked.append(part.text());
			}
		}
		String html = HtmlUtils.htmlEscape(marked.toString());
		html = BOLD.matcher(html).replaceAll("<strong>$1</strong>");
		html = ITALICS.matcher(html).replaceAll("<em>$1</em>");
		return LINK_PLACE.matcher(html).replaceAll(found -> {
			String address = HtmlUtils.htmlEscape(addresses.get(Integer.parseInt(found.group(1))));
			return Matcher.quoteReplacement(
					"<a class=\"link break-all\" href=\"" + address + "\">" + address + "</a>");
		});
	}

	/**
	 * The paragraphs and lists as they're made, line by line: lines next to each other are one paragraph, a blank
	 * line ends it, and points next to each other are one list.
	 */
	private static final class Blocks {

		private final StringBuilder html = new StringBuilder();

		private final List<String> paragraph = new ArrayList<>();

		/** The list being made: ul or ol; {@code null} outside one */
		private @Nullable String list;

		void line(String line) {
			endList();
			this.paragraph.add(line);
		}

		void point(String kind, String point) {
			endParagraph();
			if (!kind.equals(this.list)) {
				endList();
				this.html.append('<').append(kind).append('>');
				this.list = kind;
			}
			this.html.append("<li>").append(point).append("</li>");
		}

		void end() {
			endParagraph();
			endList();
		}

		private void endParagraph() {
			if (!this.paragraph.isEmpty()) {
				this.html.append("<p>").append(String.join("<br>", this.paragraph)).append("</p>");
				this.paragraph.clear();
			}
		}

		private void endList() {
			if (this.list != null) {
				this.html.append("</").append(this.list).append('>');
				this.list = null;
			}
		}

	}

}
