package com.jobwarden.cv.dto;

import org.jspecify.annotations.Nullable;

/**
 * An uploaded CV file, to show on the CV page: a PDF as it is, which the browser shows itself; a Word document as HTML;
 * a text file as its text.
 *
 * @param pdf whether it's a PDF: then neither html nor text, and the page shows the file itself
 * @param html a Word document's paragraphs, headings, lists, tables and links, every text escaped; {@code null} for the
 * others, or when it couldn't be read
 * @param text a text file's text; {@code null} for the others
 */
public record CvFilePreview(String fileName, boolean pdf, @Nullable String html, @Nullable String text) {
}
