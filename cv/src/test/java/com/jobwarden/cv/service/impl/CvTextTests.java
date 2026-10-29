package com.jobwarden.cv.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class CvTextTests {

	@Test
	void readsAPdfsText() throws IOException {
		assertThat(CvText.read(CvFileType.PDF, pdf("Senior Java Developer"))).isEqualTo("Senior Java Developer");
	}

	@Test
	void readsAWordDocumentsParagraphs() throws IOException {
		byte[] docx = docx("""
				<w:document><w:body>
				<w:p><w:r><w:t>Robert Ciap&#259;</w:t></w:r></w:p>
				<w:p><w:r><w:t xml:space="preserve">Java &amp; Kafka</w:t><w:tab/><w:t>2021</w:t></w:r></w:p>
				</w:body></w:document>""");

		assertThat(CvText.read(CvFileType.DOCX, docx)).isEqualTo("Robert Ciapă\n\nJava & Kafka\t2021");
	}

	@Test
	void readsATextFileTidied() throws IOException {
		assertThat(CvText.read(CvFileType.TEXT, "  Java\n\n\n\n\nKafka  \n".getBytes(StandardCharsets.UTF_8)))
			.isEqualTo("Java\n\nKafka");
		assertThat(CvText.read(CvFileType.TEXT, " \n ".getBytes(StandardCharsets.UTF_8))).isNull();
	}

	@Test
	void keepsAtMostSoMuchText() throws IOException {
		byte[] long_ = "x".repeat(CvText.MAX_LENGTH + 10).getBytes(StandardCharsets.UTF_8);

		assertThat(CvText.read(CvFileType.TEXT, long_)).hasSize(CvText.MAX_LENGTH);
	}

	@Test
	void needsAWordDocumentsText() {
		assertThatIOException().isThrownBy(() -> CvText.read(CvFileType.DOCX, zip("other.xml", "<x/>")));
	}

	@Test
	void tellsTheTypeFromTheNameAndChecksTheContent() throws IOException {
		assertThat(CvFileType.claimedBy("CV.PDF", "application/octet-stream")).contains(CvFileType.PDF);
		assertThat(CvFileType.claimedBy("cv", "application/pdf")).contains(CvFileType.PDF);
		assertThat(CvFileType.claimedBy("cv.docx", null)).contains(CvFileType.DOCX);
		assertThat(CvFileType.claimedBy("cv.md", "text/markdown")).contains(CvFileType.TEXT);
		assertThat(CvFileType.claimedBy("photo.png", "image/png")).isEmpty();
		assertThat(CvFileType.PDF.matches(pdf("x"))).isTrue();
		assertThat(CvFileType.DOCX.matches(docx("<w:p/>"))).isTrue();
		// A PDF in name only
		assertThat(CvFileType.PDF.matches("not a pdf".getBytes(StandardCharsets.UTF_8))).isFalse();
		assertThat(CvFileType.TEXT.matches(new byte[] { 1, 2 })).isTrue();
	}

	private static byte[] pdf(String text) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(72, 700);
				content.showText(text);
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}

	private static byte[] docx(String documentXml) throws IOException {
		return zip("word/document.xml", documentXml);
	}

	private static byte[] zip(String name, String content) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry(name));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
		}
		return out.toByteArray();
	}

}
