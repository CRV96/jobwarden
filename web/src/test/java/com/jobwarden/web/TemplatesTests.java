package com.jobwarden.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The templates keep the rules a template can break unseen. The pages run under a Content Security Policy that lets scripts come from the server's files only (web.config's
 * SecurityHeadersFilter), and HTMX is told never to evaluate code (layout.html). What needs either does nothing, or
 * worse: an hx-trigger filter is then taken as true for every event. So the templates never ask for it.
 */
class TemplatesTests {

	private static final Path TEMPLATES = Path.of("src/main/resources/templates");

	/** What would run as code: hx-trigger filters, hx-on, js: and javascript: values, inline event handlers. */
	private static final Pattern CODE = Pattern.compile(
			"hx-trigger=\"[^\"]*\\[|hx-on[:-]|hx-(?:vals|vars|headers)=[\"']\\s*(?:js|javascript):|\\son[a-z]+=\"|href=\"javascript:");

	/** A script with no src: its code would be inline. */
	private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bth:src=|[^>]*\\bsrc=)[^>]*>");

	@Test
	void noTemplateAsksForCodeThePolicyWouldntRun() throws IOException {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
				// Comments describe such things freely
				String html = Files.readString(file).replaceAll("(?s)<!--.*?-->", "");
				for (Pattern pattern : List.of(CODE, INLINE_SCRIPT)) {
					Matcher matcher = pattern.matcher(html);
					while (matcher.find()) {
						found.add(TEMPLATES.relativize(file) + ": " + matcher.group());
					}
				}
			}
		}

		assertThat(found).isEmpty();
	}

	/**
	 * What a screen reader needs: a label for every field (a {@code <label>} around it or for it, or an aria-label),
	 * a name for every button and link that shows only an icon, an alt text for every image. Those in a
	 * {@code <template>} are named by the script that copies them.
	 */
	@Test
	void everyFieldIconAndImageHasANameAScreenReaderCanSay() throws IOException {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
				Document page = Jsoup.parse(Files.readString(file).replaceAll("(?s)<!--.*?-->", ""));
				Set<String> labelled = new HashSet<>(page.select("label[for]").eachAttr("for"));
				boolean labelsFor = !page.select("label[for], label[th:for]").isEmpty();
				for (Element field : page.select("input, select, textarea")) {
					if (!FIELDS_WITHOUT_LABELS.contains(field.attr("type")) && field.closest("template") == null
							&& !named(field) && field.closest("label") == null && !labelled.contains(field.id())
							&& !(field.hasAttr("th:id") && labelsFor)) {
						found.add(TEMPLATES.relativize(file) + ": a field without a label: " + field.outerHtml());
					}
				}
				for (Element control : page.select("button, a")) {
					if (control.closest("template") == null && control.text().isBlank() && !named(control)
							&& !control.hasAttr("th:text") && !control.hasAttr("th:utext") && !control.hasAttr("th:replace")
							&& !control.hasAttr("th:insert")) {
						found.add(TEMPLATES.relativize(file) + ": an icon without a name: " + control.outerHtml());
					}
				}
				for (Element image : page.select("img:not([alt]):not([th:alt])")) {
					found.add(TEMPLATES.relativize(file) + ": an image without alt: " + image.outerHtml());
				}
			}
		}

		assertThat(found).isEmpty();
	}

	private static final Set<String> FIELDS_WITHOUT_LABELS = Set.of("hidden", "submit", "button", "reset", "image");

	private static boolean named(Element element) {
		return Stream.of("aria-label", "th:aria-label", "aria-labelledby", "th:aria-labelledby", "title", "th:title")
			.anyMatch(element::hasAttr);
	}

}
