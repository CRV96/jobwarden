package com.jobwarden.cv.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A Word document as HTML, to preview it on the CV page: its paragraphs, headings (by the styles' names, which Word
 * keeps in English whatever the language), bold, italic and underlined text, lists, tables, links and line breaks, and
 * the text boxes templates put side columns in. Not its images, fonts or layout. The HTML is this class's own: every
 * text is escaped, and only http(s) links are links.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DocxHtml {

	private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

	private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

	private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

	/** A Word document's parts are far smaller than this: anything bigger isn't one, or is a ZIP bomb. */
	private static final int MAX_PART_BYTES = 20 * 1024 * 1024;

	private static final Pattern HEADING = Pattern.compile("heading ([1-9])");

	private static final Pattern RELATIONSHIP = Pattern.compile("<Relationship\\b[^>]*>");

	private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");

	/**
	 * @throws IOException if it isn't a Word document
	 */
	static String render(byte[] docx) throws IOException {
		Map<String, byte[]> parts = parts(docx);
		byte[] document = parts.get("word/document.xml");
		if (document == null) {
			throw new IOException("Not a Word document: no word/document.xml");
		}
		Map<String, String> styles = styleNames(parts.get("word/styles.xml"));
		Map<String, String> links = links(parts.get("word/_rels/document.xml.rels"));
		try {
			return new Renderer(styles, links).render(document);
		} catch (XMLStreamException broken) {
			throw new IOException("The Word document can't be read", broken);
		}
	}

	private static Map<String, byte[]> parts(byte[] docx) throws IOException {
		Map<String, byte[]> parts = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				String name = entry.getName();
				if (name.equals("word/document.xml") || name.equals("word/styles.xml")
						|| name.equals("word/_rels/document.xml.rels")) {
					byte[] bytes = zip.readNBytes(MAX_PART_BYTES + 1);
					if (bytes.length > MAX_PART_BYTES) {
						throw new IOException(name + " is larger than " + MAX_PART_BYTES + " bytes");
					}
					parts.put(name, bytes);
				}
			}
		}
		return parts;
	}

	/** Each style's name, by its id: the id may be in the document's language, e.g. Titlu1, the name is heading 1. */
	private static Map<String, String> styleNames(byte @Nullable [] styles) throws IOException {
		Map<String, String> names = new HashMap<>();
		if (styles == null) {
			return names;
		}
		try {
			XMLStreamReader xml = reader(styles);
			String id = null;
			while (xml.hasNext()) {
				if (xml.next() != XMLStreamConstants.START_ELEMENT || !W.equals(xml.getNamespaceURI())) {
					continue;
				}
				if (xml.getLocalName().equals("style")) {
					id = xml.getAttributeValue(W, "styleId");
				} else if (xml.getLocalName().equals("name") && id != null) {
					String name = xml.getAttributeValue(W, "val");
					if (name != null) {
						names.put(id, name.toLowerCase(Locale.ROOT));
					}
				}
			}
		} catch (XMLStreamException broken) {
			throw new IOException("The Word document's styles can't be read", broken);
		}
		return names;
	}

	/** The targets of the document's external links, by relationship id. */
	private static Map<String, String> links(byte @Nullable [] relationships) {
		Map<String, String> links = new HashMap<>();
		if (relationships == null) {
			return links;
		}
		Matcher relationship = RELATIONSHIP.matcher(new String(relationships, StandardCharsets.UTF_8));
		while (relationship.find()) {
			Map<String, String> attributes = new HashMap<>();
			Matcher attribute = ATTRIBUTE.matcher(relationship.group());
			while (attribute.find()) {
				attributes.put(attribute.group(1), attribute.group(2).replace("&amp;", "&"));
			}
			if (attributes.containsKey("Id") && attributes.containsKey("Target")) {
				links.put(attributes.get("Id"), attributes.get("Target"));
			}
		}
		return links;
	}

	private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		// No DTDs, no entities from outside: a document's XML is read as it is, never fetched more of
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
		InputStream in = new ByteArrayInputStream(xml);
		return factory.createXMLStreamReader(in);
	}

	static String escape(String text) {
		StringBuilder escaped = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&' -> escaped.append("&amp;");
				case '<' -> escaped.append("&lt;");
				case '>' -> escaped.append("&gt;");
				case '"' -> escaped.append("&quot;");
				case '\'' -> escaped.append("&#39;");
				default -> escaped.append(c);
			}
		}
		return escaped.toString();
	}

	/**
	 * Walks document.xml once. A paragraph is held until it ends, as its style and list mark come first but its text
	 * after; a text box's paragraphs come in the middle of another's, and are blocks of their own.
	 */
	private static final class Renderer {

		private final Map<String, String> styles;

		private final Map<String, String> links;

		private final StringBuilder html = new StringBuilder();

		private final Deque<Paragraph> paragraphs = new ArrayDeque<>();

		/** Whether a list is open: list paragraphs in a row make one list. */
		private boolean inList;

		Renderer(Map<String, String> styles, Map<String, String> links) {
			this.styles = styles;
			this.links = links;
		}

		String render(byte[] document) throws XMLStreamException {
			XMLStreamReader xml = reader(document);
			while (xml.hasNext()) {
				int event = xml.next();
				if (event == XMLStreamConstants.START_ELEMENT) {
					start(xml);
				} else if (event == XMLStreamConstants.END_ELEMENT) {
					end(xml);
				} else if (event == XMLStreamConstants.CHARACTERS && this.paragraphs.peek() instanceof Paragraph paragraph
						&& paragraph.inText) {
					paragraph.text(xml.getText());
				}
			}
			closeList();
			return this.html.toString();
		}

		private void start(XMLStreamReader xml) throws XMLStreamException {
			if (MC.equals(xml.getNamespaceURI()) && xml.getLocalName().equals("Fallback")) {
				// The same as the Choice before it, for older Word: read once
				skip(xml);
				return;
			}
			if (!W.equals(xml.getNamespaceURI())) {
				return;
			}
			Paragraph paragraph = this.paragraphs.peek();
			switch (xml.getLocalName()) {
				case "p" -> this.paragraphs.push(new Paragraph());
				case "tbl" -> {
					closeList();
					this.html.append("<table>");
				}
				case "tr" -> this.html.append("<tr>");
				case "tc" -> this.html.append("<td>");
				case "pStyle" -> {
					if (paragraph != null) {
						paragraph.style = this.styles.getOrDefault(xml.getAttributeValue(W, "val"), "");
					}
				}
				case "numPr" -> {
					if (paragraph != null) {
						paragraph.listItem = true;
					}
				}
				case "jc" -> {
					if (paragraph != null) {
						paragraph.align = xml.getAttributeValue(W, "val");
					}
				}
				case "r" -> {
					if (paragraph != null) {
						paragraph.run = new Run();
					}
				}
				case "b", "i", "u" -> {
					if (paragraph != null && paragraph.run != null && !isOff(xml)) {
						paragraph.run.mark(xml.getLocalName());
					}
				}
				case "t" -> {
					if (paragraph != null) {
						paragraph.inText = true;
					}
				}
				case "tab" -> {
					if (paragraph != null && paragraph.run != null) {
						paragraph.text(" ");
					}
				}
				case "br", "cr" -> {
					if (paragraph != null) {
						paragraph.lineBreak();
					}
				}
				case "hyperlink" -> {
					if (paragraph != null) {
						paragraph.link = this.links.get(xml.getAttributeValue(R, "id"));
					}
				}
				// Field codes, deleted text and the document's last section: never shown
				case "instrText", "delText", "sectPr" -> skip(xml);
				default -> {
					// Nothing to show for it
				}
			}
		}

		private void end(XMLStreamReader xml) {
			if (!W.equals(xml.getNamespaceURI())) {
				return;
			}
			Paragraph paragraph = this.paragraphs.peek();
			switch (xml.getLocalName()) {
				case "p" -> write(this.paragraphs.pop());
				case "tbl" -> this.html.append("</table>");
				case "tr" -> this.html.append("</tr>");
				case "tc" -> {
					closeList();
					this.html.append("</td>");
				}
				case "t" -> {
					if (paragraph != null) {
						paragraph.inText = false;
					}
				}
				case "r" -> {
					if (paragraph != null) {
						paragraph.endRun();
					}
				}
				case "hyperlink" -> {
					if (paragraph != null) {
						paragraph.link = null;
					}
				}
				default -> {
					// Nothing to close
				}
			}
		}

		private void write(Paragraph paragraph) {
			String content = paragraph.content.toString();
			if (paragraph.listItem) {
				if (!this.inList) {
					this.html.append("<ul>");
					this.inList = true;
				}
				this.html.append("<li>").append(content).append("</li>");
				return;
			}
			closeList();
			if (content.isBlank()) {
				return;
			}
			String tag = tag(paragraph.style);
			this.html.append('<').append(tag);
			if ("center".equals(paragraph.align) || "right".equals(paragraph.align)) {
				this.html.append(" class=\"").append(paragraph.align).append('"');
			}
			this.html.append('>').append(content).append("</").append(tag).append('>');
		}

		/** title is h1, heading 1 h2, heading 2 h3, the others h4; any other paragraph p. */
		private static String tag(String style) {
			if (style.equals("title")) {
				return "h1";
			}
			Matcher heading = HEADING.matcher(style);
			if (heading.matches()) {
				return "h" + Math.min(4, Integer.parseInt(heading.group(1)) + 1);
			}
			return "p";
		}

		private void closeList() {
			if (this.inList) {
				this.html.append("</ul>");
				this.inList = false;
			}
		}

		/** b, i and u say off with val false or 0: a style turned off for one run. */
		private static boolean isOff(XMLStreamReader xml) {
			String value = xml.getAttributeValue(W, "val");
			return "false".equals(value) || "0".equals(value) || "none".equals(value);
		}

		private static void skip(XMLStreamReader xml) throws XMLStreamException {
			int depth = 1;
			while (depth > 0 && xml.hasNext()) {
				int event = xml.next();
				if (event == XMLStreamConstants.START_ELEMENT) {
					depth++;
				} else if (event == XMLStreamConstants.END_ELEMENT) {
					depth--;
				}
			}
		}

	}

	/** A paragraph as it's read: its style and list mark, then its runs. */
	private static final class Paragraph {

		private final StringBuilder content = new StringBuilder();

		private String style = "";

		private boolean listItem;

		private @Nullable String align;

		private @Nullable Run run;

		private @Nullable String link;

		private boolean inText;

		void text(String text) {
			if (this.run == null) {
				this.run = new Run();
			}
			this.run.text.append(text);
		}

		void lineBreak() {
			endRun();
			this.content.append("<br>");
		}

		void endRun() {
			Run ended = this.run;
			this.run = null;
			if (ended == null || ended.text.isEmpty()) {
				return;
			}
			String html = escape(ended.text.toString());
			for (String mark : ended.marks) {
				html = "<" + mark + ">" + html + "</" + mark + ">";
			}
			if (this.link != null && (this.link.startsWith("https://") || this.link.startsWith("http://"))) {
				html = "<a href=\"" + escape(this.link) + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + html + "</a>";
			}
			this.content.append(html);
		}

	}

	/** A run of text in one style: bold (strong), italic (em), underlined (u). */
	private static final class Run {

		private final StringBuilder text = new StringBuilder();

		private final List<String> marks = new ArrayList<>();

		void mark(String property) {
			String mark = switch (property) {
				case "b" -> "strong";
				case "i" -> "em";
				default -> "u";
			};
			if (!this.marks.contains(mark)) {
				this.marks.add(mark);
			}
		}

	}

}
