package com.jobwarden.web.view;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.jobwarden.web.constants.AppConstants.Views;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.stereotype.Component;

/**
 * The CV built from the CV page's fields, as a PDF: the same template as its preview ({@code cv/document}), rendered
 * with openhtmltopdf. The HTML goes through jsoup first, as openhtmltopdf only reads well-formed XML. Noto Sans is
 * embedded, so every language's letters show (ă, ș, ł, ß...): PDF's own fonts have only Western European ones.
 */
@Component
@RequiredArgsConstructor
public class CvPdf {

	/** The family the template's styles name. Its files are also the preview's web fonts, under static/fonts. */
	static final String FONT_FAMILY = "Noto Sans";

	private static final Map<String, FontFace> FONTS = Map.of(
			"/static/fonts/NotoSans-Regular.ttf", new FontFace(400, FontStyle.NORMAL),
			"/static/fonts/NotoSans-Bold.ttf", new FontFace(700, FontStyle.NORMAL),
			"/static/fonts/NotoSans-Italic.ttf", new FontFace(400, FontStyle.ITALIC));

	private final ITemplateEngine templates;

	/**
	 * @param variables what the template reads, as for its preview
	 */
	public byte[] render(Map<String, Object> variables, Locale locale) {
		String html = this.templates.process(Views.CV_DOCUMENT, new Context(locale, variables));
		ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		PdfRendererBuilder builder = new PdfRendererBuilder();
		builder.useFastMode();
		FONTS.forEach((file, face) -> builder.useFont(() -> font(file), FONT_FAMILY, face.weight(), face.style(), true));
		builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), null);
		builder.toStream(pdf);
		try {
			builder.run();
		} catch (Exception ex) {
			throw new IllegalStateException("Couldn't make the CV's PDF", ex);
		}
		return pdf.toByteArray();
	}

	private static InputStream font(String file) {
		return Objects.requireNonNull(CvPdf.class.getResourceAsStream(file), file);
	}

	private record FontFace(int weight, FontStyle style) {
	}

}
