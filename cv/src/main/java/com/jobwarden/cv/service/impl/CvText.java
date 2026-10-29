package com.jobwarden.cv.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;

/**
 * The text of a CV file, for Claude to read: a PDF's with PDFBox, a Word document's from the XML inside it, a text
 * file as it is. Tidied (no runs of blank lines) and at most {@link #MAX_LENGTH} characters long.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CvText {

	static final int MAX_LENGTH = 100_000;

	/** A Word document's text is far smaller than this: anything bigger isn't one, or is a ZIP bomb. */
	private static final int MAX_XML_BYTES = 20 * 1024 * 1024;

	private static final Pattern PARAGRAPH_END = Pattern.compile("</w:p>|<w:br\\s*/>|<w:cr\\s*/>");

	private static final Pattern TAB = Pattern.compile("<w:tab\\s*/>");

	private static final Pattern TAG = Pattern.compile("<[^>]*>");

	private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);");

	private static final Pattern BLANK_LINES = Pattern.compile("(?:[ \\t]*\\R){3,}");

	/**
	 * @return the text, or {@code null} when there's none, e.g. a scanned PDF
	 * @throws IOException if the file can't be read as its type
	 */
	static @Nullable String read(CvFileType type, byte[] content) throws IOException {
		String text = switch (type) {
			case PDF -> pdf(content);
			case DOCX -> docx(content);
			case TEXT -> new String(content, StandardCharsets.UTF_8);
		};
		return tidy(text);
	}

	private static String pdf(byte[] content) throws IOException {
		try (PDDocument document = Loader.loadPDF(content)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static String docx(byte[] content) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if ("word/document.xml".equals(entry.getName())) {
					return fromWordXml(new String(readAtMost(zip, MAX_XML_BYTES), StandardCharsets.UTF_8));
				}
			}
		}
		throw new IOException("Not a Word document: no word/document.xml");
	}

	static String fromWordXml(String xml) {
		String text = TAG.matcher(TAB.matcher(PARAGRAPH_END.matcher(xml).replaceAll("\n")).replaceAll("\t")).replaceAll("");
		Matcher entity = ENTITY.matcher(text);
		StringBuilder unescaped = new StringBuilder();
		while (entity.find()) {
			entity.appendReplacement(unescaped, Matcher.quoteReplacement(unescape(entity.group(1))));
		}
		return entity.appendTail(unescaped).toString();
	}

	private static String unescape(String entity) {
		return switch (entity) {
			case "amp" -> "&";
			case "lt" -> "<";
			case "gt" -> ">";
			case "quot" -> "\"";
			case "apos" -> "'";
			default -> {
				int codePoint = entity.startsWith("#x") ? Integer.parseInt(entity.substring(2), 16)
						: Integer.parseInt(entity.substring(1));
				yield Character.toString(codePoint);
			}
		};
	}

	private static byte[] readAtMost(InputStream in, int max) throws IOException {
		byte[] bytes = in.readNBytes(max + 1);
		if (bytes.length > max) {
			throw new IOException("The document's text is larger than " + max + " bytes");
		}
		return bytes;
	}

	private static @Nullable String tidy(String text) {
		String tidied = BLANK_LINES.matcher(text.replace("\u0000", "")).replaceAll("\n\n").strip();
		if (tidied.isEmpty()) {
			return null;
		}
		return (tidied.length() <= MAX_LENGTH) ? tidied : tidied.substring(0, MAX_LENGTH);
	}

}
