package com.jobwarden.cv.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class DocxHtmlTests {

	private static final String NAMESPACES = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
			+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
			+ "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" "
			+ "xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"";

	/** A Romanian Word's style ids: the names are English. */
	private static final String STYLES = "<w:styles " + NAMESPACES + ">"
			+ "<w:style w:styleId=\"Titlu\"><w:name w:val=\"Title\"/></w:style>"
			+ "<w:style w:styleId=\"Titlu1\"><w:name w:val=\"heading 1\"/></w:style>"
			+ "</w:styles>";

	private static final String RELATIONSHIPS = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId5\" Type=\"hyperlink\" Target=\"https://github.com/robert?tab=repositories&amp;q=x\" TargetMode=\"External\"/>"
			+ "<Relationship Id=\"rId6\" Type=\"hyperlink\" Target=\"javascript:alert(1)\" TargetMode=\"External\"/>"
			+ "</Relationships>";

	@Test
	void showsTheDocumentsTextWithItsHeadingsListsTablesAndLinks() throws IOException {
		String html = DocxHtml.render(docx("""
				<w:p><w:pPr><w:pStyle w:val="Titlu"/><w:jc w:val="center"/></w:pPr><w:r><w:t>Robert Ciapă</w:t></w:r></w:p>
				<w:p><w:pPr><w:pStyle w:val="Titlu1"/></w:pPr><w:r><w:t>Experience</w:t></w:r></w:p>
				<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Senior Java Developer</w:t></w:r><w:r><w:tab/><w:t xml:space="preserve"> at </w:t></w:r>\
				<w:r><w:rPr><w:i/><w:b w:val="0"/></w:rPr><w:t>Deloitte</w:t></w:r><w:r><w:br/><w:t>2021 – now</w:t></w:r></w:p>
				<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Payments &amp; Kafka</w:t></w:r></w:p>
				<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Spring Boot</w:t></w:r></w:p>
				<w:p><w:hyperlink r:id="rId5"><w:r><w:t>github.com/robert</w:t></w:r></w:hyperlink></w:p>
				<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Java</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>8 years</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
				"""));

		assertThat(html).isEqualTo("<h1 class=\"center\">Robert Ciapă</h1><h2>Experience</h2>"
				+ "<p><strong>Senior Java Developer</strong>  at <em>Deloitte</em><br>2021 – now</p>"
				+ "<ul><li>Payments &amp; Kafka</li><li>Spring Boot</li></ul>"
				+ "<p><a href=\"https://github.com/robert?tab=repositories&amp;q=x\" target=\"_blank\" rel=\"noopener noreferrer\">"
				+ "github.com/robert</a></p>"
				+ "<table><tr><td><p>Java</p></td><td><p>8 years</p></td></tr></table>");
	}

	@Test
	void readsATextBoxOnceAndNeverMoreThanText() throws IOException {
		String html = DocxHtml.render(docx("""
				<w:p><w:r><w:t>Before</w:t></w:r><w:r><mc:AlternateContent><mc:Choice><w:drawing><wps:txbx><w:txbxContent>\
				<w:p><w:r><w:t>Skills in the side column</w:t></w:r></w:p></w:txbxContent></wps:txbx></w:drawing></mc:Choice>\
				<mc:Fallback><w:pict><w:txbxContent><w:p><w:r><w:t>Skills in the side column</w:t></w:r></w:p></w:txbxContent>\
				</w:pict></mc:Fallback></mc:AlternateContent></w:r></w:p>
				<w:p><w:r><w:instrText>HYPERLINK "x"</w:instrText><w:t>&lt;script&gt;alert(1)&lt;/script&gt;</w:t></w:r></w:p>
				<w:p><w:hyperlink r:id="rId6"><w:r><w:t>Click</w:t></w:r></w:hyperlink></w:p>
				"""));

		assertThat(html).isEqualTo("<p>Skills in the side column</p><p>Before</p>"
				+ "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p><p>Click</p>");
	}

	@Test
	void refusesWhatIsntAWordDocument() {
		assertThatIOException().isThrownBy(() -> DocxHtml.render("Not a ZIP".getBytes(StandardCharsets.UTF_8)));
	}

	private static byte[] docx(String body) throws IOException {
		return zip(Map.of("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document " + NAMESPACES
				+ "><w:body>" + body.replace("\n", "") + "<w:sectPr/></w:body></w:document>", "word/styles.xml", STYLES,
				"word/_rels/document.xml.rels", RELATIONSHIPS));
	}

	private static byte[] zip(Map<String, String> files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, String> file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

}
