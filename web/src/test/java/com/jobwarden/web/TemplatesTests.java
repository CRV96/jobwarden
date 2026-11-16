package com.jobwarden.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules a template can break unseen. The pages run under a Content Security Policy that lets scripts come from the
 * server's files only (web.config's SecurityHeadersFilter), and HTMX is told never to evaluate code (layout.html). What
 * needs either does nothing, or worse: an hx-trigger filter is then taken as true for every event. So the templates
 * never ask for it. And a screen reader needs a name for every field, icon and image.
 * <p>
 * The templates are parsed (Jsoup), so an attribute is found however it's quoted; comments, which describe such things
 * freely, aren't read.
 */
class TemplatesTests {

	private static final Path TEMPLATES = Path.of("src/main/resources/templates");

	/** Inline event handlers: onclick, and Thymeleaf's th:onclick. */
	private static final Pattern EVENT_HANDLER = Pattern.compile("(?:th:)?on[a-z]+");

	/** hx-on:click, hx-on--after-request, and the older hx-on. */
	private static final Pattern HX_ON = Pattern.compile("(?:data-)?hx-on(?:[:-].*)?");

	/** Where a [filter] would be code. */
	private static final Pattern HX_TRIGGER = Pattern.compile("(?:th:|data-)?hx-trigger");

	/** Where a js: or javascript: value would be code. */
	private static final Pattern HX_VALUES = Pattern.compile("(?:th:|data-)?hx-(?:vals|vars|headers)");

	/** Addresses, where javascript: would be code. */
	private static final Pattern ADDRESS = Pattern.compile("(?:th:)?(?:href|src|action|formaction)");

	private static final Set<String> FIELDS_WITHOUT_LABELS = Set.of("hidden", "submit", "button", "reset", "image");

	/** A fragment's name and parameters: tag-field(name, inputId). */
	private static final Pattern FRAGMENT = Pattern.compile("([\\w-]+)\\s*\\(([^)]*)\\)");

	@Test
	void noTemplateAsksForCodeThePolicyWouldntRun() throws IOException {
		List<String> found = new ArrayList<>();
		for (Path file : templates()) {
			Document page = parse(file);
			for (Element element : page.getAllElements()) {
				for (Attribute attribute : element.attributes()) {
					if (isCode(attribute.getKey(), attribute.getValue())) {
						found.add(TEMPLATES.relativize(file) + ": " + attribute.html());
					}
				}
			}
			for (Element script : page.select("script:not([src]):not([th:src])")) {
				found.add(TEMPLATES.relativize(file) + ": an inline script: " + script.outerHtml());
			}
		}

		assertThat(found).isEmpty();
	}

	/**
	 * What a screen reader needs: a label for every field (a {@code <label>} around it or for it, or an aria-label),
	 * a name for every button and link that shows only an icon, an alt text for every image. Those in a
	 * {@code <template>} are named by the script that copies them. A field whose id the template computes
	 * ({@code th:id}) needs a label whose {@code th:for} is the same expression; in a fragment that takes the id as a
	 * parameter, each call of it in the page passes an id that a label is for.
	 */
	@Test
	void everyFieldIconAndImageHasANameAScreenReaderCanSay() throws IOException {
		List<String> found = new ArrayList<>();
		for (Path file : templates()) {
			String html = Files.readString(file);
			Document page = Jsoup.parse(html);
			Set<String> labelsFor = new HashSet<>(page.select("label[for]").eachAttr("for"));
			Set<String> labelsThFor = new HashSet<>(page.select("label[th:for]").eachAttr("th:for"));
			for (Element field : page.select("input, select, textarea")) {
				boolean labelled = field.closest("label") != null || labelsFor.contains(field.id())
						|| (field.hasAttr("th:id") && (labelsThFor.contains(field.attr("th:id"))
								|| labelledByEachCall(html, field, labelsFor)));
				if (!FIELDS_WITHOUT_LABELS.contains(field.attr("type")) && field.closest("template") == null
						&& !named(field) && !labelled) {
					found.add(TEMPLATES.relativize(file) + ": a field without a label: " + field.outerHtml());
				}
			}
			for (Element control : page.select("button, a")) {
				if (control.closest("template") == null && control.text().isBlank() && !named(control)
						&& Stream.of("th:text", "th:utext", "th:replace", "th:insert").noneMatch(control::hasAttr)) {
					found.add(TEMPLATES.relativize(file) + ": an icon without a name: " + control.outerHtml());
				}
			}
			for (Element image : page.select("img:not([alt]):not([th:alt])")) {
				found.add(TEMPLATES.relativize(file) + ": an image without alt: " + image.outerHtml());
			}
		}

		assertThat(found).isEmpty();
	}

	static boolean isCode(String name, String value) {
		String lowerValue = value.strip().toLowerCase(Locale.ROOT);
		return EVENT_HANDLER.matcher(name).matches() || HX_ON.matcher(name).matches()
				|| (HX_TRIGGER.matcher(name).matches() && value.contains("["))
				|| (HX_VALUES.matcher(name).matches() && (lowerValue.startsWith("js:") || lowerValue.startsWith("javascript:")))
				|| (ADDRESS.matcher(name).matches() && lowerValue.startsWith("javascript:"));
	}

	@Test
	void findsCodeHoweverItsQuoted() {
		Document page = Jsoup.parse("""
				<button hx-trigger='click[ctrlKey]'>Search</button>
				<a href = 'JavaScript:void(0)' onclick='go()'>Go</a>
				<div hx-on:click="go()" hx-vals='js:{a: 1}'></div>""");

		List<String> code = page.getAllElements()
			.stream()
			.flatMap(element -> element.attributes().asList().stream())
			.filter(attribute -> isCode(attribute.getKey(), attribute.getValue()))
			.map(Attribute::getKey)
			.toList();

		assertThat(code).containsExactlyInAnyOrder("hx-trigger", "href", "onclick", "hx-on:click", "hx-vals");
	}

	/**
	 * Whether the field's th:id is one of its fragment's parameters ({@code th:fragment="tag-field(name, inputId)"},
	 * {@code th:id="${inputId}"}), and each call of the fragment in the page ({@code ~{:: tag-field('titles',
	 * 'search-titles')}}) passes for it an id a label is for. Not when it can't tell: a missing label then shows.
	 */
	static boolean labelledByEachCall(String html, Element field, Set<String> labelsFor) {
		Matcher id = Pattern.compile("\\$\\{(\\w+)}").matcher(field.attr("th:id"));
		Element fragment = field.closest("[th:fragment]");
		Matcher signature = (fragment != null) ? FRAGMENT.matcher(fragment.attr("th:fragment")) : null;
		if (!id.matches() || signature == null || !signature.matches()) {
			return false;
		}
		int index = List.of(signature.group(2).split("\\s*,\\s*")).indexOf(id.group(1));
		Matcher call = Pattern.compile(":: " + Pattern.quote(signature.group(1)) + "\\(([^)]*)\\)").matcher(html);
		int calls = 0;
		while (index >= 0 && call.find()) {
			String[] arguments = call.group(1).split("\\s*,\\s*");
			if (index >= arguments.length || !arguments[index].matches("'[\\w-]+'")
					|| !labelsFor.contains(arguments[index].substring(1, arguments[index].length() - 1))) {
				return false;
			}
			calls++;
		}
		return calls > 0;
	}

	@Test
	void aFragmentsFieldIsLabelledOnlyWhereEveryCallOfItIs() {
		String html = """
				<label for="a-title">Title</label>
				<div th:replace="~{:: field('a-title')}"></div>
				<div th:replace="~{:: field('b-title')}"></div>
				<div th:fragment="field(inputId)"><input type="text" th:id="${inputId}"></div>""";
		Element field = Jsoup.parse(html).selectFirst("input");

		assertThat(field).isNotNull();
		assertThat(labelledByEachCall(html, field, Set.of("a-title"))).isFalse();
		assertThat(labelledByEachCall(html, field, Set.of("a-title", "b-title"))).isTrue();
	}

	private static List<Path> templates() throws IOException {
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			return files.filter(path -> path.toString().endsWith(".html")).toList();
		}
	}

	private static Document parse(Path file) throws IOException {
		return Jsoup.parse(Files.readString(file));
	}

	private static boolean named(Element element) {
		return Stream.of("aria-label", "th:aria-label", "aria-labelledby", "th:aria-labelledby", "title", "th:title")
			.anyMatch(element::hasAttr);
	}

}
